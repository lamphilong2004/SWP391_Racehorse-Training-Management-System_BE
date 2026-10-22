"""Flow 2 — Training Management journey against real APIs. Run after flow1.py (uses flow-state.json)."""
import json, datetime
from rh import call, save

st = json.load(open('flow-state.json'))
hid, U = st['horseId'], st['users']
today = datetime.date.today()
utc = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
iso = lambda d: d.isoformat()
show = lambda label, d: print('  ', label, json.dumps(d)[:400])

# --- Club Manager: staff skills so recommendations have data
s, skills = call('CLUB_MANAGER', 'GET', '/api/admin/skills', expect=200)
spec = [k['id'] for k in skills if k['kind'] == 'TRAINER_SPECIALIZATION']
rider = [k['id'] for k in skills if k['kind'] == 'RIDER_SKILL']
for role, ids in (('TRAINER', spec[:2]), ('WORK_RIDER', rider[:2])):
    s, u = call('CLUB_MANAGER', 'GET', f'/api/admin/users/{U[role]}', expect=200)
    call('CLUB_MANAGER', 'PUT', f'/api/admin/users/{U[role]}', {'fullName': u['fullName'], 'email': u['email'], 'roleCode': role,
                                                            'experienceYears': 5, 'experienceLevel': 'SENIOR', 'skillIds': ids}, expect=200)

# --- Head Trainer: exercise catalog + template library
s, ex1 = call('HEAD_TRAINER', 'POST', '/api/exercises', {'name': 'Steady canter', 'exerciseType': 'CANTER', 'targetMetric': 'distance'}, expect=[200, 201])
s, ex2 = call('HEAD_TRAINER', 'POST', '/api/exercises', {'name': 'Walk cool-down', 'exerciseType': 'WALK', 'targetMetric': 'duration'}, expect=[200, 201])
call('WORK_RIDER', 'POST', '/api/exercises', {'name': 'Denied'}, expect=403)
s, tpl = call('HEAD_TRAINER', 'POST', '/api/program-templates', {
    'name': 'Base conditioning 6w', 'goals': 'Aerobic base', 'requiredSkillIds': spec[:1],
    'phases': [{'phaseOrder': 1, 'name': 'Foundation', 'objective': 'Aerobic base', 'intensityGuidance': 'LOW-MEDIUM'},
               {'phaseOrder': 2, 'name': 'Build', 'objective': 'Tempo', 'intensityGuidance': 'MEDIUM'}]}, expect=201)
show('template', tpl)
call('TRAINER', 'POST', '/api/program-templates', {'name': 'x', 'phases': [{'phaseOrder': 1, 'name': 'x'}]}, expect=403)
tid = tpl['template']['id']
s, det = call('HEAD_TRAINER', 'GET', f'/api/program-templates/{tid}', expect=200)
show('template detail keys', list(det.keys()))
ver = tpl['version']
vid = ver['id']
vphases = ver.get('phases') or []
s, rec = call('HEAD_TRAINER', 'GET', f'/api/horses/{hid}/template-recommendations', expect=200); show('tpl rec', rec)
s, rec = call('HEAD_TRAINER', 'GET', f'/api/horses/{hid}/trainer-recommendations?templateVersionId={vid}', expect=200); show('trainer rec', rec)

# --- Head Trainer: horse program assignment
p1s, p1e, p2e = today, today + datetime.timedelta(days=20), today + datetime.timedelta(days=41)
phases = [{'templateVersionPhaseId': (vphases[0].get('id') if vphases else None), 'phaseOrder': 1, 'name': 'Foundation', 'objective': 'Aerobic base',
           'startDate': iso(p1s), 'endDate': iso(p1e), 'intensityMin': 'LOW', 'intensityMax': 'MEDIUM', 'workload': 'Moderate'},
          {'templateVersionPhaseId': (vphases[1].get('id') if len(vphases) > 1 else None), 'phaseOrder': 2, 'name': 'Build', 'objective': 'Tempo',
           'startDate': iso(p1e + datetime.timedelta(days=1)), 'endDate': iso(p2e), 'intensityMin': 'MEDIUM', 'intensityMax': 'MEDIUM', 'workload': 'Moderate'}]
body = {'horseId': hid, 'templateVersionId': vid, 'name': 'Silver Comet base 6w', 'goals': 'Aerobic base', 'periodStart': iso(p1s), 'periodEnd': iso(p2e),
        'phases': phases, 'trainer': {'trainerUserId': U['TRAINER'], 'effectiveFrom': iso(today), 'reason': 'Best fit', 'warningsAcknowledged': True}}
call('TRAINER', 'POST', '/api/training-programs', body, expect=403)
s, prog = call('HEAD_TRAINER', 'POST', '/api/training-programs', body, expect=201)
show('program', prog)
pid = prog['id']
s, prog = call('TRAINER', 'GET', f'/api/training-programs/{pid}', expect=200)
phase_id = prog['phases'][0]['id']
s, plist = call('TRAINER', 'GET', '/api/training-programs', expect=200); show('trainer programs', plist)

st.update(programId=pid, phaseId=phase_id, exercises=[ex1['id'], ex2['id']])
json.dump(st, open('flow-state.json', 'w'))
save('flow2a-evidence.json')
