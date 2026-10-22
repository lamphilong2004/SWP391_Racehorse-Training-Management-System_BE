"""Flow 3 — Medical/Health journey + cross-flow training gating. Run after flow2b.py (uses flow-state.json)."""
import json, datetime, time
from rh import call, save

st = json.load(open('flow-state.json'))
hid, U, phase_id = st['horseId'], st['users'], st['phaseId']
ex1, ex2 = st['exercises']
utcnow = lambda: datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
today = datetime.date.today()
show = lambda label, d: print('  ', label, json.dumps(d)[:400])

# Incident from the interrupted Flow 2 session -> Vet opens a case from it
s, inc = call('VETERINARIAN', 'GET', f'/api/incidents?horseId={hid}', expect=200)
incident = next(i for i in inc['items'] if i['status'] == 'REPORTED')
show('incident times', {'observedAt': incident['observedAt'], 'reportedAt': incident['reportedAt']})
call('TRAINER', 'POST', f"/api/incidents/{incident['id']}/review", {'action': 'OPEN_CASE'}, expect=403)
s, d = call('VETERINARIAN', 'POST', f"/api/incidents/{incident['id']}/review", {'action': 'OPEN_CASE'}, expect=200); show('review', d)
cid = d['medicalCaseId']
call('VETERINARIAN', 'POST', f"/api/incidents/{incident['id']}/review", {'action': 'OPEN_CASE'}, expect=409)

# Clinical record
call('TRAINER', 'POST', f'/api/medical/cases/{cid}/examinations', {'symptoms': 'x', 'examinedAt': utcnow().isoformat()}, expect=403)
s, exam = call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/examinations', {'symptoms': 'Short stride left fore', 'diagnosis': 'Mild tendon strain',
             'severity': 'MILD', 'examinedAt': utcnow().isoformat(), 'healthStatus': 'INJURED', 'bodyCondition': 'Good'}, expect=201); show('exam', exam)
s, inj = call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/injuries', {'bodyRegion': 'LEFT_FORE_TENDON', 'severity': 'MILD', 'recoveryStatus': 'NOT_RECOVERED',
             'veterinaryExaminationId': exam['id'], 'side': 'LEFT'}, expect=201); show('injury', inj)
s, plan = call('VETERINARIAN', 'PUT', f'/api/medical/cases/{cid}/treatment-plan', {
    'treatmentMedicationInstructions': 'NSAID per label 3 days', 'restInstructions': 'Walk only', 'careInstructions': 'Cold hose 2x daily',
    'groomTasks': [{'instruction': 'Cold hose left fore 15 min', 'taskDate': today.isoformat(), 'scheduledSlot': 'AM', 'assignedGroomUserId': U['GROOM']}],
    'reexaminationDate': (today + datetime.timedelta(days=3)).isoformat()}, expect=200); show('plan', plan)

# Restriction LIMITED with a prohibited exercise -> Trainer suitability shows medical hard block for that exercise
call('TRAINER', 'POST', f'/api/horses/{hid}/medical-restrictions', {'level': 'LIMITED', 'reason': 'x', 'effectiveAt': utcnow().isoformat()}, expect=403)
s, r = call('VETERINARIAN', 'POST', f'/api/horses/{hid}/medical-restrictions', {'level': 'LIMITED', 'reason': 'Tendon strain: walk only',
            'effectiveAt': utcnow().isoformat(), 'reviewDate': (today + datetime.timedelta(days=3)).isoformat(), 'medicalCaseId': cid}, expect=201); show('restriction', r)
rid = r['id']
s, d = call('VETERINARIAN', 'PUT', f'/api/medical/restrictions/{rid}/exercise-rules', {'rules': [{'exerciseId': ex1, 'ruleType': 'PROHIBITED'}, {'exerciseId': ex2, 'ruleType': 'ALLOWED'}]}, expect=200)
s, d = call('TRAINER', 'GET', f'/api/horses/{hid}/training-availability', expect=200); show('availability', d)
import random
start = utcnow() + datetime.timedelta(days=random.randint(1, 18), minutes=random.randint(0, 700) * 2)
sb = {'horseProgramPhaseId': phase_id, 'scheduledStart': start.isoformat(), 'scheduledEnd': (start + datetime.timedelta(minutes=30)).isoformat(), 'intensity': 'LOW',
      'workRiderUserId': U['WORK_RIDER'], 'exercises': [{'exerciseDefinitionId': ex1, 'exerciseOrder': 1, 'durationMin': 5, 'intensity': 'LOW'}]}
s, d = call('TRAINER', 'POST', '/api/training-sessions/suitability-check', sb, expect=200); show('suitability LIMITED', d)
call('TRAINER', 'POST', '/api/training-sessions', dict(sb, riderWarningAcknowledged=True), expect=[409, 422])
sb['exercises'] = [{'exerciseDefinitionId': ex2, 'exerciseOrder': 1, 'durationMin': 10, 'intensity': 'LOW'}]
s, ok_sess = call('TRAINER', 'POST', '/api/training-sessions', dict(sb, riderWarningAcknowledged=True), expect=201)

# LOCKED blocks whole session scheduling (one active restriction per horse: second create conflicts; Vet lifts LIMITED then sets LOCKED)
call('VETERINARIAN', 'POST', f'/api/horses/{hid}/medical-restrictions', {'level': 'LOCKED', 'reason': 'dup', 'effectiveAt': utcnow().isoformat()}, expect=409)
call('VETERINARIAN', 'POST', f'/api/medical/restrictions/{rid}/lift', {}, expect=200)
s, r2 = call('VETERINARIAN', 'POST', f'/api/horses/{hid}/medical-restrictions', {'level': 'LOCKED', 'reason': 'Re-check pending', 'effectiveAt': utcnow().isoformat(), 'medicalCaseId': cid}, expect=201)
s, d = call('TRAINER', 'GET', f'/api/horses/{hid}/training-availability', expect=200); show('availability LOCKED', d)
start2 = start + datetime.timedelta(hours=2)
sb2 = dict(sb, scheduledStart=start2.isoformat(), scheduledEnd=(start2 + datetime.timedelta(minutes=30)).isoformat())
call('TRAINER', 'POST', '/api/training-sessions', dict(sb2, riderWarningAcknowledged=True), expect=[409, 422])

# Groom care task
s, tasks = call('GROOM', 'GET', '/api/groom/treatment-tasks', expect=200); show('groom tasks', tasks)
items = tasks.get('items', tasks) if isinstance(tasks, dict) else tasks
task = next(t for t in items if t['horseId'] == hid and t['status'] == 'PENDING')
call('WORK_RIDER', 'POST', f"/api/groom/treatment-tasks/{task['id']}/complete", {}, expect=403)
s, d = call('GROOM', 'POST', f"/api/groom/treatment-tasks/{task['id']}/complete", {}, expect=200); show('task done', d)
call('GROOM', 'POST', f"/api/groom/treatment-tasks/{task['id']}/complete", {}, expect=409)
call('VETERINARIAN', 'GET', f'/api/medical/cases/{cid}/treatment-tasks', expect=200)

# Role-scoped reads
s, d = call('TRAINER', 'GET', '/api/medical/cases', expect=200); show('trainer cases', d)
s, d = call('TRAINER', 'GET', f'/api/medical/cases/{cid}', expect=200); show('trainer case detail', d)
s, d = call('HEAD_TRAINER', 'GET', f'/api/medical/cases/{cid}', expect=[200, 403]); show('head trainer case', d)
s, d = call('HORSE_OWNER', 'GET', f'/api/owner/horses/{hid}/health-summary', expect=200); show('owner health', d)
call('HORSE_OWNER', 'GET', f'/api/medical/cases/{cid}', expect=403)
call('GROOM', 'GET', '/api/medical/cases', expect=[200, 403])

# Closure precondition, recovery, lift, close
call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/close', expect=409)
s, d = call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/re-examinations', {'recoveryStatus': 'RECOVERED', 'findings': 'Sound at trot', 'examinedAt': utcnow().isoformat(),
            'healthStatus': 'FIT', 'severity': 'MILD'}, expect=201); show('re-exam', d)
for x in (r2['id'],):
    call('VETERINARIAN', 'POST', f'/api/medical/restrictions/{x}/lift', {}, expect=200)
s, d = call('TRAINER', 'GET', f'/api/horses/{hid}/training-availability', expect=200); show('availability after lift', d)
call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/close', expect=409)
s, d = call('VETERINARIAN', 'POST', f"/api/incidents/{incident['id']}/review", {'action': 'RESOLVE'}, expect=200); show('incident resolved', d['status'])
s, d = call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/close', expect=200); show('closed', d)
call('VETERINARIAN', 'POST', f'/api/medical/cases/{cid}/examinations', {'symptoms': 'late', 'examinedAt': utcnow().isoformat()}, expect=409)
s, d = call('VETERINARIAN', 'GET', '/api/medical/herd-health', expect=200); show('herd health', d)
s, d = call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}/timeline', expect=200); show('timeline', d)

st.update(caseId=cid, okSessionId=ok_sess['id'] if isinstance(ok_sess, dict) else None)
json.dump(st, open('flow-state.json', 'w'))
save('flow3-evidence.json')
