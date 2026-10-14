// Validate Redocly JSON bundles of the actual medical and shared contracts.
// node docs/api/verify-core006.cjs <medical-bundle.json> <shared-bundle.json>
const fs = require('node:fs');
const assert = require('node:assert/strict');
const d=JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const shared=JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
let checks=0;
const check=(condition,message)=>{checks++;assert.ok(condition,message);};
const s=d.components.schemas;
const get=(p,m)=>d.paths[p]?.[m];
const decision=d['x-approved-flow3-decisions'];
const expectedOwners={
 'CORE-019':'Ngô Minh Tiến',
 'CORE-026':'Ngô Minh Tiến',
 'CORE-035':'Ngô Minh Tiến',
 'CORE-045':'Ngô Minh Tiến',
 'CORE-046':'Nguyễn Đình Minh Đức'
};
check(Object.keys(d['x-downstream-ownership']??{}).length===Object.keys(expectedOwners).length,'No extra downstream owner mapping');
for(const [task,owner] of Object.entries(expectedOwners))check(d['x-downstream-ownership']?.[task]===owner,`Current owner ${task}`);
const roles=['CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM','HORSE_OWNER'];
const required={
 '/api/horses/{horseId}/baseline-examinations':['post'],
 '/api/horses/{horseId}/weights':['get','post'],
 '/api/incidents':['get','post'],
 '/api/incidents/{incidentId}':['get'],
 '/api/incidents/{incidentId}/review':['post'],
 '/api/medical/cases':['get','post'],
 '/api/medical/cases/{caseId}':['get'],
 '/api/medical/cases/{caseId}/examinations':['post'],
 '/api/medical/cases/{caseId}/injuries':['post'],
 '/api/medical/cases/{caseId}/treatment-plan':['get','put'],
 '/api/medical/cases/{caseId}/treatment-tasks':['get'],
 '/api/medical/cases/{caseId}/re-examinations':['post'],
 '/api/medical/cases/{caseId}/close':['post'],
 '/api/groom/treatment-tasks':['get'],
 '/api/groom/treatment-tasks/{taskId}/complete':['post'],
 '/api/horses/{horseId}/training-availability':['get'],
 '/api/horses/{horseId}/medical-restrictions':['post'],
 '/api/medical/restrictions':['get'],
 '/api/medical/restrictions/{restrictionId}/lift':['post'],
 '/api/medical/restrictions/{restrictionId}/exercise-rules':['put'],
 '/api/medical/thresholds':['get'],
 '/api/horses/{horseId}/medical-thresholds':['get','put'],
 '/api/horses/{horseId}/medical-thresholds/end':['post'],
 '/api/medical/herd-health':['get']
};
for(const [p,methods]of Object.entries(required))for(const m of methods)check(get(p,m),`${m} ${p} missing`);
const ids=new Set();
for(const [p,pi]of Object.entries(d.paths))for(const [m,o]of Object.entries(pi))if(o.operationId){
 check(!ids.has(o.operationId),'Duplicate operationId');ids.add(o.operationId);
 check(o['x-roles']?.length>0,`Missing roles ${p}`);
 check(o['x-roles'].every(r=>roles.includes(r)),`Invalid role ${p}`);
 check(JSON.stringify(o.security??d.security)==='[{"bearerAuth":[]}]',`JWT absent ${p}`);
 for(const code of ['400','401','403','404','409'])check(o.responses[code]?.content?.['application/json']?.schema?.$ref==='#/components/schemas/ErrorEnvelope',`Error ${code} ${p}`);
 check(!o.responses['422'],`Unsupported 422 ${p}`);
 check(o.description?.length>50,`Missing behavior ${p}`);
 if(m!=='get'&&!p.startsWith('/api/groom/')&&p!=='/api/incidents')check(JSON.stringify(o['x-roles'])==='["VETERINARIAN"]',`Clinical writer ${p}`);
 if(p.startsWith('/api/groom/'))check(JSON.stringify(o['x-roles'])==='["GROOM"]',`Groom roles ${p}`);
}
const expectedReadRoles={
 '/api/horses/{horseId}/weights':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN','GROOM'],
 '/api/incidents':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM'],
 '/api/incidents/{incidentId}':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM'],
 '/api/medical/cases':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN'],
 '/api/medical/cases/{caseId}':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN'],
 '/api/horses/{horseId}/training-availability':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM'],
 '/api/medical/restrictions':['CLUB_MANAGER','HEAD_TRAINER','TRAINER','VETERINARIAN'],
 '/api/medical/thresholds':['HEAD_TRAINER','TRAINER','VETERINARIAN'],
 '/api/horses/{horseId}/medical-thresholds':['HEAD_TRAINER','TRAINER','VETERINARIAN'],
 '/api/medical/herd-health':['VETERINARIAN']
};
for(const [p,rs]of Object.entries(expectedReadRoles))check(JSON.stringify(get(p,'get')['x-roles'])===JSON.stringify(rs),`Matrix drift ${p}`);
check(JSON.stringify(get('/api/incidents','post')['x-roles'])==='["HEAD_TRAINER","TRAINER","WORK_RIDER","GROOM"]','Incident creation roles');
for(const n of ['ApiError','ErrorEnvelope','ErrorDetail','ErrorCode'])check(JSON.stringify(s[n])===JSON.stringify(shared.components.schemas[n]),`Shared drift ${n}`);
const enums={HealthStatus:'FIT MONITORING INJURED QUARANTINED',ClinicalSeverity:'MILD MODERATE SEVERE',IncidentStatus:'REPORTED UNDER_REVIEW RESOLVED',MedicalCaseStatus:'OPEN CLOSED',TreatmentTaskStatus:'PENDING COMPLETED',MonitoringMetric:'HEART_RATE',RestrictionLevel:'LIMITED LOCKED',TrainingAvailability:'AVAILABLE LIMITED LOCKED'};
for(const [n,values]of Object.entries(enums))check(JSON.stringify(s[n].enum)===JSON.stringify(values.split(' ')),`Enum ${n}`);
function walk(v,path='root'){
 if(!v||typeof v!=='object')return;
 for(const [k,x]of Object.entries(v)){
  if(k==='properties')for(const [name,p]of Object.entries(x))if(name==='id'||name.endsWith('Id'))check(p.type==='integer'&&p.format==='int64',`ID type ${path}.${name}`);
  walk(x,path+'.'+k);
 }
}
walk(s);
for(const [n,p]of Object.entries(d.components.parameters))if(n.endsWith('Id'))check(p.schema.type==='integer'&&p.schema.format==='int64',`Path ID ${n}`);
const body=s.IncidentCreateRequest;
for(const n of ['preSessionCheckId','trainingSessionId','careTaskId'])check(body.properties[n]&&!body.required.includes(n),`Optional context ${n}`);
for(let bits=0;bits<8;bits++){
 const names=['preSessionCheckId','trainingSessionId','careTaskId'].filter((_,i)=>bits&(1<<i));
 const accepted=body.allOf.every(c=>!c.not.required.every(n=>names.includes(n)));
 check(accepted===(names.length<=1),`Context XOR ${bits}`);
}
check(decision?.abnormalPreSessionCheck?.persistCheck===true,'ABNORMAL check persisted');
check(decision.abnormalPreSessionCheck.overallResult==='ABNORMAL','ABNORMAL result');
check(decision.abnormalPreSessionCheck.sessionState==='SCHEDULED','ABNORMAL leaves session SCHEDULED');
check(decision.abnormalPreSessionCheck.startBlocked===true,'ABNORMAL blocks Start');
check(decision.abnormalPreSessionCheck.incidentCreation==='EXPLICIT_ONLY','No automatic Incident');
check(/ABNORMAL.*persisted.*SCHEDULED.*blocks Start.*never automatically creates an Incident/i.test(get('/api/incidents','post').description),'Incident operation reflects pre-check decision');
check(body.description.includes('does not automatically create an Incident'),'Incident request requires explicit creation');

const assignment=decision.treatmentGroomAssignment;
check(assignment.schemaAdditionApproved==='care_task.assigned_groom_user_id BIGINT NULL FK app_user(id)','Approved nullable Groom FK recorded');
check(assignment.treatmentTaskRequiresAssignedGroom===true,'Treatment assignment mandatory by business validation');
check(assignment.completedByUserIdSource==='JWT','Completion actor from JWT');
check(assignment.migrationImplementedInCore006===false,'Migration not claimed in CORE-006');
const groomId=n=>s[n].properties.assignedGroomUserId;
for(const n of ['GroomTaskInstruction','TreatmentTask','GroomTreatmentTask']){
 check(groomId(n)?.type==='integer'&&groomId(n)?.format==='int64'&&groomId(n)?.minimum===1,`${n} assignedGroomUserId type`);
 check(s[n].required.includes('assignedGroomUserId'),`${n} requires assignedGroomUserId`);
}
check(!s.TreatmentTaskCompleteRequest.properties.completedByUserId,'Client cannot set completion actor');
check(s.TreatmentTask.properties.completedByUserId?.description.includes('JWT'),'Veterinarian follow-up actor provenance');
check(/assigned_groom_user_id equals the authenticated Groom/.test(get('/api/groom/treatment-tasks','get').description),'Groom list row scope');
check(/assigned_groom_user_id to equal the authenticated Groom/.test(get('/api/groom/treatment-tasks/{taskId}/complete','post').description),'Groom completion row scope');
check(get('/api/groom/treatment-tasks','get').description.includes('Fail closed until'),'Migration fail-closed list');
check(get('/api/groom/treatment-tasks/{taskId}/complete','post').description.includes('Fail closed until'),'Migration fail-closed completion');
const groomNames=new Set(['id','horseId','assignedGroomUserId','instruction','taskDate','scheduledSlot','status','completedAt']);
check(s.GroomTreatmentTask.additionalProperties===false,'Groom strict projection');
check(Object.keys(s.GroomTreatmentTask.properties).every(n=>groomNames.has(n)),'Groom clinical leak');
const data=o=>Object.values(o.responses).find(r=>r.content?.['application/json']?.schema?.properties?.data)?.content['application/json'].schema.properties.data;
check(data(get('/api/groom/treatment-tasks','get')).$ref.endsWith('/GroomTreatmentTasksPage'),'Groom list projection');
check(data(get('/api/groom/treatment-tasks/{taskId}/complete','post')).$ref.endsWith('/GroomTreatmentTask'),'Groom completion projection');
check(s.GroomTreatmentTasksPage.properties.items.items.$ref.endsWith('/GroomTreatmentTask'),'Groom item projection');

const gate=decision.restrictionExerciseGate;
check(gate.locked==='BLOCK_ALL','LOCKED blocks every exercise');
check(gate.limitedProhibited==='DENY_FIRST','LIMITED prohibited precedence');
check(gate.limitedWithAllowedRules==='REQUIRE_ALLOWED_MATCH_AND_NO_PROHIBITED_MATCH','LIMITED allowed-list semantics');
check(gate.limitedWithoutAllowedRules==='ALLOW_UNLESS_PROHIBITED','LIMITED default semantics');
const rule=s.RestrictionExerciseRule;
check(rule.properties.exerciseId?.type==='integer'&&rule.properties.exerciseId?.format==='int64','Exercise ID request type');
check(!rule.properties.exerciseDefinitionId,'Old exerciseDefinitionId request removed');
check(rule.properties.exerciseTypeNote?.minLength===1,'Nonempty note/pattern target');
check(JSON.stringify(rule.properties.ruleType.enum)==='["ALLOWED","PROHIBITED"]','Rule type values');
check(JSON.stringify(rule.oneOf?.map(x=>x.required))==='[["exerciseId"],["exerciseTypeNote"]]','Exactly one exercise target schema');
for(const exerciseId of [false,true])for(const exerciseTypeNote of [false,true]){
 const present=new Set([exerciseId&&'exerciseId',exerciseTypeNote&&'exerciseTypeNote'].filter(Boolean));
 const matches=rule.oneOf.filter(branch=>branch.required.every(name=>present.has(name))).length;
 check((matches===1)===(exerciseId!==exerciseTypeNote),`Exercise target XOR ${exerciseId}/${exerciseTypeNote}`);
}
check(/LOCKED blocks all/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability LOCKED behavior');
check(/PROHIBITED rule first/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability PROHIBITED precedence');
check(/when ALLOWED rules exist/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability ALLOWED semantics');

const threshold=decision.thresholdResolution;
check(threshold.owner==='CORE-019'&&threshold.consumer==='CORE-030','Threshold ownership');
check(threshold.globalSource==='MANAGED_APPLICATION_CONFIG','Global managed config');
check(threshold.demoSeedBpm===180&&threshold.warningFormulaFromCritical==='NONE','Demo seed is not warning rule');
for(const [path,method] of [
 ['/api/medical/thresholds','get'],
 ['/api/horses/{horseId}/medical-thresholds','get'],
 ['/api/horses/{horseId}/medical-thresholds','put'],
 ['/api/horses/{horseId}/medical-thresholds/end','post']
])check(get(path,method)['x-implementation-task'].startsWith('CORE-019'),'Threshold operation owned by CORE-019');
check(get('/api/horses/{horseId}/medical-thresholds','get').operationId==='getHorseThresholdOverrides','Stable threshold GET operationId');
check(get('/api/horses/{horseId}/medical-thresholds','get').summary==='Read effective per-horse medical threshold','Effective threshold GET semantics');
check(/managed global application configuration/.test(get('/api/horses/{horseId}/medical-thresholds','get').description),'Effective fallback source');
check(/overlapping effective intervals return 409/.test(get('/api/horses/{horseId}/medical-thresholds','put').description),'Override interval conflict');
check(/180 bpm.*demo\/default seed/.test(get('/api/medical/thresholds','get').description),'180 bpm demo only');
check(!/WARNING\s*=\s*critical\s*-\s*10/i.test(JSON.stringify(d)),'No universal warning formula');

check(s.MedicalCaseSummary.additionalProperties===false&&!s.MedicalCaseSummary.properties.reason,'Case summary clinical leak');
check(data(get('/api/medical/cases/{caseId}','get')).$ref.endsWith('/MedicalCaseRead'),'Role-selected case detail');
check(!s.TreatmentPlan.properties.status,'Invented plan status');
check(!s.MedicalRestrictionRequest.properties.expiresAt,'Invented restriction expiry');
check(s.InjuryRequest.properties.position3d.type==='string'&&s.InjuryRequest.properties.position3d.maxLength===100,'3D SQL mapping');
check(/lifted_at IS NULL/.test(get('/api/horses/{horseId}/training-availability','get').description),'Derived availability');
check(/409/.test(get('/api/horses/{horseId}/medical-restrictions','post').description),'Active conflict');
check(/409/.test(get('/api/horses/{horseId}/baseline-examinations','post').description),'Duplicate baseline');
check(get('/api/medical/cases/{caseId}/examinations','post').description.includes('horse.health_status'),'Health persistence');
check(get('/api/incidents','post').description.includes('never automatically creates an Incident'),'Explicit incident');
check(!/pending approval|unconfirmed|provisional|taxonomy pending|canonical.*not yet confirmed|DRAFT|UNVERIFIED|implementation ownership to confirm|fieldErrors/i.test(JSON.stringify(d)),'Unresolved placeholder language');
check(!Object.keys(d.paths).some(p=>/preventive|nutrition|finance/.test(p)),'Scope expansion');
console.log(`PASS: ${checks} contract checks; ${ids.size} operations. Documented decision/schema gaps remain integration gates.`);
