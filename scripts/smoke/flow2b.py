"""Flow 2b — session scheduling, Groom precheck, Work Rider execution, Trainer evaluation. Run after flow2.py."""
import json, datetime, time
from rh import call, save

st = json.load(open('flow-state.json'))
hid, U, pid, phase_id = st['horseId'], st['users'], st['programId'], st['phaseId']
ex1, ex2 = st['exercises']
today = datetime.date.today().isoformat()
show = lambda label, d: print('  ', label, json.dumps(d)[:400])
utcnow = lambda: datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
exs = [{'exerciseDefinitionId': ex1, 'exerciseOrder': 1, 'distanceM': 2000, 'durationMin': 8, 'repetitions': 1, 'intensity': 'MEDIUM', 'surface': 'TURF'},
       {'exerciseDefinitionId': ex2, 'exerciseOrder': 2, 'durationMin': 10, 'intensity': 'LOW', 'surface': 'TURF'}]
checks = {k: {'result': 'NORMAL', 'observation': 'ok'} for k in ('GENERAL_CONDITION', 'APPETITE_BEHAVIOUR', 'LEGS_HOOVES', 'MOVEMENT', 'TEMPERATURE')}


def schedule(start, first, minutes=1):
    sb = {'horseProgramPhaseId': phase_id, 'scheduledStart': start.isoformat(), 'scheduledEnd': (start + datetime.timedelta(minutes=minutes)).isoformat(),
          'intensity': 'MEDIUM', 'workRiderUserId': U['WORK_RIDER'], 'exercises': exs}
    if first:
        s, d = call('TRAINER', 'POST', '/api/training-sessions/suitability-check', sb, expect=200); show('suitability', d)
        s, d = call('TRAINER', 'POST', '/api/training-sessions/work-rider-recommendations', sb, expect=200); show('rider rec', d)
    s, sess = call('TRAINER', 'POST', '/api/training-sessions', dict(sb, surface='TURF', focus='Aerobic', riderWarningAcknowledged=True), expect=201)
    sid = sess['id']
    if first:
        call('TRAINER', 'PATCH', f'/api/training-sessions/{sid}', {'focus': 'Aerobic base'}, expect=200)
        call('TRAINER', 'PUT', f'/api/training-sessions/{sid}/exercises', {'exercises': exs}, expect=200)
        s, cands = call('TRAINER', 'GET', f'/api/training-sessions/{sid}/groom-candidates', expect=200); show('groom cands', cands)
    call('TRAINER', 'POST', f'/api/training-sessions/{sid}/groom-assignment', {'groomUserId': U['GROOM']}, expect=200)
    return sid


# Free the Work Rider: Trainer reschedules leftover SCHEDULED sessions from earlier runs to tomorrow (legit Trainer action)
from urllib.parse import quote
f, t2 = quote((utcnow() - datetime.timedelta(days=1)).isoformat()), quote((utcnow() + datetime.timedelta(hours=6)).isoformat())
s, items = call('TRAINER', 'GET', f'/api/training-sessions?from={f}&to={t2}&status=SCHEDULED', expect=200)
base = utcnow().replace(hour=1, minute=0, second=0) + datetime.timedelta(days=1)
for i, x in enumerate(items):
    t = base + datetime.timedelta(hours=i)
    call('TRAINER', 'PATCH', f"/api/training-sessions/{x['id']}", {'scheduledStart': t.isoformat(), 'scheduledEnd': (t + datetime.timedelta(minutes=30)).isoformat()}, expect=200)
now = utcnow()
sid = schedule(now + datetime.timedelta(seconds=75), True)
sid2 = schedule(now + datetime.timedelta(seconds=140), False)

# Gates before precheck / before scheduled time
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/start', expect=409)
s, d = call('GROOM', 'GET', f'/api/groom/sessions?date={today}', expect=200); print('   groom sessions today', len(d) if isinstance(d, list) else d)
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/pre-session-check', {'checks': checks}, expect=403)
call('GROOM', 'POST', f'/api/training-sessions/{sid}/pre-session-check', {'checks': {'GENERAL_CONDITION': {'result': 'NORMAL'}}}, expect=400)
s, d = call('GROOM', 'POST', f'/api/training-sessions/{sid}/pre-session-check', {'checks': checks}, expect=201); show('precheck', d.get('overallResult'))
call('GROOM', 'POST', f'/api/training-sessions/{sid}/pre-session-check', {'checks': checks}, expect=409)
call('GROOM', 'POST', f'/api/training-sessions/{sid2}/pre-session-check', {'checks': checks}, expect=201)
s, d = call('WORK_RIDER', 'GET', '/api/work-rider/assignments', expect=200); print('   rider assignments', len(d))
wait = (now + datetime.timedelta(seconds=142) - utcnow()).total_seconds()
if wait > 0:
    print(f'   waiting {wait:.0f}s for scheduledStart'); time.sleep(wait)

# Session 1: complete path
call('GROOM', 'POST', f'/api/training-sessions/{sid}/start', expect=403)
s, d = call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/start', expect=200); show('start', d['status'])
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/start', expect=409)
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/metrics', {'recordedAt': utcnow().isoformat(), 'heartRate': 150, 'speedKmh': 38.5, 'distanceM': 800}, expect=[200, 201])
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/pause', expect=403)
s, d = call('TRAINER', 'POST', f'/api/training-sessions/{sid}/pause', expect=200); show('pause', d['status'])
call('TRAINER', 'POST', f'/api/training-sessions/{sid}/pause', expect=409)
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/complete', expect=409)
s, d = call('TRAINER', 'POST', f'/api/training-sessions/{sid}/resume', expect=200); show('resume', d['status'])
call('TRAINER', 'POST', f'/api/training-sessions/{sid}/complete', expect=403)
s, d = call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/complete', expect=200); show('complete', d)
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/complete', expect=409)
fb = {'horseResponse': 'Willing', 'balance': 'Good', 'breathing': 'Normal', 'strideFeeling': 'Even', 'notes': 'Good session'}
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/rider-feedback', {'responses': fb}, expect=[200, 201])
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid}/evaluation', {'result': 'MET'}, expect=403)
call('TRAINER', 'POST', f'/api/training-sessions/{sid}/evaluation', {'result': 'GREAT'}, expect=400)
s, d = call('TRAINER', 'POST', f'/api/training-sessions/{sid}/evaluation', {'result': 'MET', 'evaluatedSummary': 'Targets met', 'recommendation': 'Progress'}, expect=[200, 201]); show('evaluation', d)
call('TRAINER', 'POST', f'/api/training-sessions/{sid}/evaluation', {'result': 'MET'}, expect=409)

# Session 2: interrupt path with medically relevant stop -> incident
call('TRAINER', 'POST', f'/api/training-sessions/{sid2}/start', expect=200)
s, d = call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid2}/report-issue', {'description': 'Short stride left fore', 'incidentType': 'HOOF_OR_LEG_PROBLEM', 'observedSeverity': 'MEDIUM'}, expect=[200, 201]); show('issue', d)
s, d = call('TRAINER', 'POST', f'/api/training-sessions/{sid2}/stop', {'reason': 'Lameness suspected', 'medicallyRelevant': True, 'incidentType': 'HOOF_OR_LEG_PROBLEM', 'observedSeverity': 'MEDIUM'}, expect=409); show('stop after rider interrupt', d)
call('WORK_RIDER', 'POST', f'/api/training-sessions/{sid2}/rider-feedback', {'responses': {'preInterruptionDescription': 'Uneven stride after 600m', 'notes': 'Stopped by trainer'}}, expect=[200, 201])
call('TRAINER', 'POST', f'/api/training-sessions/{sid2}/evaluation', {'result': 'NOT_MET', 'evaluatedSummary': 'Interrupted', 'recommendation': 'Vet review'}, expect=[200, 201])

# Reads / history / scope
s, d = call('TRAINER', 'GET', f'/api/training-sessions/{sid}/result', expect=200); show('result', d)
s, d = call('HEAD_TRAINER', 'GET', f'/api/training-programs/{pid}/progress', expect=200); show('progress', d)
s, d = call('HEAD_TRAINER', 'GET', f'/api/training-programs/{pid}/history', expect=200); show('history', d)
s, d = call('HEAD_TRAINER', 'GET', f'/api/horses/{hid}/performance', expect=200); show('performance', d)
s, d = call('WORK_RIDER', 'GET', '/api/work-rider/history', expect=200); show('rider history', d)
s, d = call('VETERINARIAN', 'GET', '/api/incidents', expect=200); show('incidents (vet)', d)
call('HORSE_OWNER', 'GET', f'/api/owner/horses/{hid}/training-summary', expect=200)
call('HORSE_OWNER', 'GET', f'/api/training-sessions/{sid}', expect=403)

st.update(sessionId=sid, interruptedSessionId=sid2)
json.dump(st, open('flow-state.json', 'w'))
save('flow2b-evidence.json')
