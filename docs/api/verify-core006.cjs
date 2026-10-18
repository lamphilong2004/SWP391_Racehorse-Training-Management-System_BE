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

const reviewOperation=get('/api/incidents/{incidentId}/review','post');
const reviewRule=decision.incidentReview;
const transitions=reviewRule.transitions;
check(reviewOperation['x-state-transitions']?.$ref==='#/x-approved-flow3-decisions/incidentReview','Review operation binds approved transitions');
check(JSON.stringify(s.IncidentStatus.enum)==='["REPORTED","UNDER_REVIEW","RESOLVED"]','Approved Incident states');
check(JSON.stringify(s.IncidentReviewRequest.properties.action.enum)===JSON.stringify(Object.keys(transitions)),'Review request actions match transitions');
check(reviewOperation.requestBody.content['application/json'].schema.$ref==='#/components/schemas/IncidentReviewRequest','Review request schema');
check(JSON.stringify(transitions.OPEN_CASE.from)==='["REPORTED"]'&&transitions.OPEN_CASE.to==='UNDER_REVIEW','OPEN_CASE transition');
check(transitions.OPEN_CASE.requiresUnlinkedIncident===true&&transitions.OPEN_CASE.caseDisposition==='CREATE_OPEN_SAME_HORSE','OPEN_CASE creates same-horse OPEN case');
check(JSON.stringify(transitions.LINK_CASE.from)==='["REPORTED"]'&&transitions.LINK_CASE.to==='UNDER_REVIEW','LINK_CASE transition');
check(transitions.LINK_CASE.requiresUnlinkedIncident===true&&transitions.LINK_CASE.caseDisposition==='LINK_EXISTING_OPEN_SAME_HORSE','LINK_CASE requires existing OPEN same-horse case');
check(JSON.stringify(transitions.RESOLVE.from)==='["REPORTED","UNDER_REVIEW"]'&&transitions.RESOLVE.to==='RESOLVED','RESOLVE direct and reviewed transitions');
check(transitions.RESOLVE.preservesLinkedCase===true&&transitions.RESOLVE.closesMedicalCase===false,'RESOLVE retains link and leaves case open');
check(reviewRule.terminalState==='RESOLVED'&&reviewRule.invalidReviewHttpStatus===409,'Terminal Incident and invalid review conflict');
check(reviewRule.reviewedByUserIdSource==='JWT','Review actor from JWT');
check(reviewOperation.responses['409'].content['application/json'].schema.$ref==='#/components/schemas/ErrorEnvelope'&&/invalid transition.*repeated review.*wrong horse.*CLOSED case/i.test(reviewOperation.responses['409'].description),'Review conflict response');
check(/Re-review.*backward transitions.*changing a linked case.*auto-reopen/.test(reviewOperation.description),'Review operation rejects re-review, reversal and relinking');
check(/REPORTED unlinked Incident.*UNDER_REVIEW.*RESOLVE accepts REPORTED or UNDER_REVIEW.*terminal RESOLVED/s.test(s.IncidentReviewRequest.description),'Review request transition semantics');
function reviewOutcome(action,state,linkedCase,targetCase){
 const rule=transitions[action];
 if(!rule?.from.includes(state)||rule.requiresUnlinkedIncident&&linkedCase)return 409;
 if(action==='LINK_CASE'&&(!targetCase||targetCase.status!=='OPEN'||!targetCase.sameHorse))return 409;
 return rule.to;
}
for(const [action,state,linkedCase,targetCase,expected]of [
 ['OPEN_CASE','REPORTED',false,null,'UNDER_REVIEW'],
 ['OPEN_CASE','REPORTED',true,null,409],
 ['OPEN_CASE','UNDER_REVIEW',false,null,409],
 ['LINK_CASE','REPORTED',false,{status:'OPEN',sameHorse:true},'UNDER_REVIEW'],
 ['LINK_CASE','REPORTED',false,{status:'CLOSED',sameHorse:true},409],
 ['LINK_CASE','REPORTED',false,{status:'OPEN',sameHorse:false},409],
 ['LINK_CASE','REPORTED',true,{status:'OPEN',sameHorse:true},409],
 ['LINK_CASE','UNDER_REVIEW',false,{status:'OPEN',sameHorse:true},409],
 ['RESOLVE','REPORTED',false,null,'RESOLVED'],
 ['RESOLVE','UNDER_REVIEW',true,null,'RESOLVED'],
 ['RESOLVE','RESOLVED',true,null,409],
])check(reviewOutcome(action,state,linkedCase,targetCase)===expected,`Incident transition ${action}/${state}/${linkedCase}/${targetCase?.status??'none'}`);

const caseCreateOperation=get('/api/medical/cases','post');
const caseCreateSchema=s.MedicalCaseCreateRequest;
check(caseCreateOperation.requestBody.content['application/json'].schema.$ref==='#/components/schemas/MedicalCaseCreateRequest','Independent case POST uses MedicalCaseCreateRequest');
check(JSON.stringify(Object.keys(caseCreateSchema.properties))==='["horseId","reason"]','Case create permits only horseId and reason');
check(!Object.hasOwn(caseCreateSchema.properties,'incidentId'),'Case create cannot accept incidentId');
check(JSON.stringify(caseCreateSchema.required)==='["horseId","reason"]','Case create requires horseId and reason');
check(caseCreateSchema.additionalProperties===false,'Case create rejects extra properties');
check(/Create an independent MedicalCase with status OPEN/.test(caseCreateOperation.description),'Case POST creates independent OPEN case');
check(/This operation cannot link an Incident/.test(caseCreateOperation.description),'Case POST has no Incident linkage');
check(/only by OPEN_CASE or LINK_CASE through POST \/api\/incidents\/\{incidentId\}\/review/.test(caseCreateOperation.description),'Review is the exclusive Incident linkage path');
check(!/incidentId|link.*Incident/i.test(caseCreateSchema.properties.reason.description),'Case reason does not imply Incident linkage');
check(/unsupported incidentId/.test(caseCreateOperation.responses['400'].description),'Case POST rejects bypass field with validation error');
check(!Object.hasOwn(s.IncidentCreateRequest.properties,'medicalCaseId'),'Incident reporting cannot bypass review by setting medicalCaseId');
check(ids.size===29,'No additional operation may bypass Incident Review');
for(const [path,pathItem]of Object.entries(d.paths))for(const [method,operation]of Object.entries(pathItem)){
 if(!operation.operationId||method==='get'||path==='/api/incidents/{incidentId}/review')continue;
 const inlineRequestSchema=operation.requestBody?.content?.['application/json']?.schema;
 const requestRef=inlineRequestSchema?.$ref;
 const requestSchema=requestRef?.startsWith('#/components/schemas/')?s[requestRef.split('/').at(-1)]:inlineRequestSchema;
 check(!requestSchema?.properties?.incidentId,`No alternate Incident linkage request ${method} ${path}`);
 check(requestRef!=='#/components/schemas/IncidentReviewRequest',`Review actions are exclusive to Review POST ${method} ${path}`);
}

const closeOperation=get('/api/medical/cases/{caseId}/close','post');
const closeRule=decision.medicalCaseClosure;
check(closeOperation['x-close-conditions']?.$ref==='#/x-approved-flow3-decisions/medicalCaseClosure','Close operation binds approved gates');
check(JSON.stringify(s.MedicalCaseStatus.enum)==='["OPEN","CLOSED"]'&&closeRule.requiresStatus==='OPEN','Only OPEN case may close');
check(closeRule.minimumClinicalExaminations===1&&JSON.stringify(closeRule.clinicalExaminationTypes)==='["EXAMINATION","RE_EXAMINATION"]','At least one clinical examination');
check(JSON.stringify(closeRule.recoveryGate.whenAny)==='["INJURY_EXISTS","TREATMENT_PLAN_EXISTS"]'&&closeRule.recoveryGate.minimumReexaminations===1&&closeRule.recoveryGate.latestRecoveryOutcome==='RECOVERED','Latest re-exam recovery gate');
check(closeRule.treatmentTasksCompleteIfPlanExists===true&&closeRule.linkedRestrictionsLifted===true&&closeRule.linkedIncidentsResolved===true,'Treatment, restriction and incident closure gates');
check(closeRule.injuryRecoveryStatusIndependentGate===false,'Injury recovery_status is not an independent close gate');
check(closeRule.cascadeIncidentResolve===false&&closeRule.cascadeRestrictionLift===false&&closeRule.cascadeGroomCompletion===false,'Close does not cascade into other actions');
check(closeRule.invalidCloseHttpStatus===409&&closeOperation.responses['409'].content['application/json'].schema.$ref==='#/components/schemas/ErrorEnvelope','Invalid close returns shared 409');
check(/completed_at and completed_by_user_id present/.test(closeOperation.description),'Completed treatment tasks use persisted completion pair');
check(/Do not resolve Incidents, lift restrictions or complete Groom tasks/.test(closeOperation.description),'Close operation is non-cascading');
function mayClose(state){
 const gate=closeRule;
 if(state.status!==gate.requiresStatus)return false;
 if(state.exams.filter(type=>gate.clinicalExaminationTypes.includes(type)).length<gate.minimumClinicalExaminations)return false;
 if(gate.recoveryGate.whenAny.some(x=>(x==='INJURY_EXISTS'&&state.injury)||(x==='TREATMENT_PLAN_EXISTS'&&state.plan))){
  if(state.reexams.length<gate.recoveryGate.minimumReexaminations||state.reexams.at(-1)!==gate.recoveryGate.latestRecoveryOutcome)return false;
 }
 return (!state.plan||state.tasksComplete)&&state.restrictionsLifted&&state.incidentsResolved;
}
const closeBase={status:'OPEN',exams:['EXAMINATION'],injury:false,plan:false,reexams:[],tasksComplete:true,restrictionsLifted:true,incidentsResolved:true};
for(const [change,expected]of [
 [{},true], [{status:'CLOSED'},false], [{exams:[]},false],
 [{injury:true},false], [{injury:true,reexams:['RECOVERED']},true],
 [{injury:true,reexams:['RECOVERED'],injuryRecoveryStatus:'NOT_RECOVERED'},true],
 [{injury:true,reexams:['RECOVERED','IMPROVING']},false],
 [{plan:true,reexams:['RECOVERED'],tasksComplete:false},false],
 [{plan:true,reexams:['RECOVERED'],tasksComplete:true},true],
 [{restrictionsLifted:false},false], [{incidentsResolved:false},false],
])check(mayClose({...closeBase,...change})===expected,`Case closure gate ${JSON.stringify(change)}`);

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
check(JSON.stringify(rule.properties.ruleType.enum)==='["ALLOW","PROHIBIT"]','API rule type values');
check(/API values ALLOW and PROHIBIT map explicitly to persisted ALLOWED and PROHIBITED/.test(rule.description),'API-to-database enum mapping');
check(JSON.stringify(rule.oneOf?.map(x=>x.required))==='[["exerciseId"],["exerciseTypeNote"]]','Exactly one exercise target schema');
for(const exerciseId of [false,true])for(const exerciseTypeNote of [false,true]){
 const present=new Set([exerciseId&&'exerciseId',exerciseTypeNote&&'exerciseTypeNote'].filter(Boolean));
 const matches=rule.oneOf.filter(branch=>branch.required.every(name=>present.has(name))).length;
 check((matches===1)===(exerciseId!==exerciseTypeNote),`Exercise target XOR ${exerciseId}/${exerciseTypeNote}`);
}
check(/LOCKED blocks all/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability LOCKED behavior');
check(/PROHIBIT rule first/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability PROHIBIT precedence');
check(/when ALLOW rules exist/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability ALLOW semantics');
const effective=decision.restrictionEffectiveAt;
const restrictionCreate=get('/api/horses/{horseId}/medical-restrictions','post');
check(restrictionCreate['x-effective-at-validation']?.$ref==='#/x-approved-flow3-decisions/restrictionEffectiveAt','Restriction create binds approved time validation');
check(effective.futureAllowed===false&&effective.compareTo==='SERVER_NOW'&&effective.effectiveImmediatelyOnCreate===true,'Option A immediate restriction');
check(effective.futureErrorHttpStatus===400&&effective.futureErrorCode==='VALIDATION_ERROR','Future restriction uses 400 validation error');
check(s.MedicalRestrictionRequest.required.includes('effectiveAt')&&s.MedicalRestrictionRequest.properties.effectiveAt.format==='date-time','Required effectiveAt timestamp');
check(/no later than the server's current time.*future values return 400 VALIDATION_ERROR/i.test(s.MedicalRestrictionRequest.properties.effectiveAt.description),'Request rejects future effectiveAt');
check(/Reject effectiveAt later than server now with 400 VALIDATION_ERROR/.test(restrictionCreate.description),'Create operation rejects future effectiveAt');
check(/effectiveAt later than server now/.test(restrictionCreate.responses['400'].description)&&restrictionCreate.responses['400'].content['application/json'].schema.$ref==='#/components/schemas/ErrorEnvelope','Future effectiveAt response');
check(/immediately effective LIMITED\/LOCKED/.test(get('/api/horses/{horseId}/training-availability','get').description),'Availability is immediate');
check(!/future-dated rows|future scheduled restriction is allowed/i.test(JSON.stringify(d)),'No future scheduled restriction semantics');
function acceptsEffectiveAt(effectiveAt,serverNow){return effective.futureAllowed||effective.compareTo==='SERVER_NOW'&&effectiveAt<=serverNow;}
for(const [offset,expected]of [[-1,true],[0,true],[1,false]])check(acceptsEffectiveAt(1000+offset,1000)===expected,`effectiveAt offset ${offset}`);

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
