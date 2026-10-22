"""Flow 1 — Horse Management journey against real APIs (FE proxy -> Spring Boot -> SQL Server)."""
import json, uuid, datetime
from rh import call, save, TOKENS

today = datetime.date.today().isoformat()
code = 'RH-' + uuid.uuid4().hex[:8].upper()
state = {}

# RBAC denials first
call(None, 'GET', '/api/horses', expect=401)
call('WORK_RIDER', 'POST', '/api/horses', {'horseCode': code, 'name': 'Denied'}, expect=403)

s, h = call('CLUB_MANAGER', 'POST', '/api/horses', {'horseCode': code, 'name': 'Silver Comet', 'breed': 'Thoroughbred', 'sex': 'MARE',
                                                  'foaledDate': '2022-03-14', 'countryOfOrigin': 'VN', 'registrationNo': 'REG-' + code}, expect=201)
print('   horse', json.dumps(h)[:300])
hid = h['id']; state['horseId'] = hid
call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}', expect=200)
s, lst = call('CLUB_MANAGER', 'GET', '/api/horses', expect=200)
print('   list contains horse:', any(x.get('id') == hid for x in (lst.get('items') if isinstance(lst, dict) else lst)))

s, d = call('CLUB_MANAGER', 'PUT', f'/api/horses/{hid}/pedigree', {'sire': {'name': 'Northern Star'}, 'dam': {'name': 'Golden Rain'}}, expect=200)
print('   pedigree', json.dumps(d)[:200])
s, users = call('CLUB_MANAGER', 'GET', '/api/admin/users?size=50', expect=200)
uid = {u['roles'][0]: u['id'] for u in users['items']}
state['users'] = uid
s, d = call('CLUB_MANAGER', 'POST', f'/api/horses/{hid}/ownerships', {'ownerUserId': uid['HORSE_OWNER'], 'sharePercentage': 100, 'effectiveFrom': today, 'isManagingOwner': True}, expect=201)
print('   ownership', json.dumps(d)[:200])
s, boxes = call('CLUB_MANAGER', 'GET', '/api/stable-boxes', expect=200)
box = next(b for b in boxes if b.get('status') == 'FREE')
s, d = call('CLUB_MANAGER', 'POST', f'/api/horses/{hid}/stable-assignments', {'stableBoxId': box['id'], 'effectiveFrom': today, 'reason': 'Initial box'}, expect=201)
print('   stable', json.dumps(d)[:200])
s, d = call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}/activation-status', expect=200)
print('   activation before baseline', json.dumps(d)[:300])

call('TRAINER', 'POST', f'/api/horses/{hid}/baseline-examinations', {'weightKg': 480, 'healthStatus': 'FIT'}, expect=403)
s, d = call('VETERINARIAN', 'POST', f'/api/horses/{hid}/baseline-examinations', {'weightKg': 482.5, 'healthStatus': 'FIT', 'clinicalNotes': 'Baseline normal'}, expect=201)
print('   baseline', json.dumps(d)[:300])
s, d = call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}/activation-status', expect=200)
print('   activation after baseline', json.dumps(d)[:300])
call('CLUB_MANAGER', 'POST', f'/api/horses/{hid}/readiness-review', expect=403)
s, d = call('HEAD_TRAINER', 'POST', f'/api/horses/{hid}/readiness-review', expect=204)
print('   readiness', json.dumps(d)[:300])
s, d = call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}', expect=200)
print('   detail status', d.get('status'), d.get('activationStatus'), json.dumps(d)[:300])
s, d = call('HORSE_OWNER', 'GET', '/api/owner/horses', expect=200)
print('   owner sees', json.dumps(d)[:200])
call('HORSE_OWNER', 'GET', f'/api/owner/horses/{hid}/profile', expect=200)
call('CLUB_MANAGER', 'GET', f'/api/horses/{hid}/timeline', expect=200)
json.dump(state, open('flow-state.json', 'w'))
save('flow1-evidence.json')
