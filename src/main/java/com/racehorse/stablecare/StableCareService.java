package com.racehorse.stablecare;

import com.racehorse.auth.*;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.common.jdbc.GeneratedIdentity;
import com.racehorse.notification.NotificationApi.Category;
import com.racehorse.notification.NotificationPublisher;
import com.racehorse.training.TrainingCalendar;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.racehorse.stablecare.StableCareApi.*;

@Service
public class StableCareService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final TrainingCalendar calendar;
    private final NotificationPublisher notifications;
    public StableCareService(JdbcTemplate jdbc,CurrentActorProvider actors,TrainingCalendar calendar,NotificationPublisher notifications) {
        this.jdbc=jdbc;this.actors=actors;this.calendar=calendar;this.notifications=notifications;
    }
    private ActorContext require(RoleCode... roles) {
        var actor=actors.currentActor().orElseThrow(()->error(ErrorCode.UNAUTHORIZED,"Sign in to continue."));
        if(Arrays.stream(roles).noneMatch(actor.roles()::contains))throw error(ErrorCode.FORBIDDEN,"This role cannot perform the stable-care action.");
        return actor;
    }
    private ActorContext readCare(){return require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN);}
    @Transactional(readOnly=true) public List<Horse> horses(){
        readCare();return jdbc.query("SELECT id,name,horse_code FROM dbo.horse ORDER BY name,id",(r,n)->new Horse(r.getLong(1),r.getString(2),r.getString(3)));
    }
    @Transactional(readOnly=true) public List<Person> grooms(){
        require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER);
        return jdbc.query("""
            SELECT u.id,u.full_name FROM dbo.app_user u WHERE u.account_status='ACTIVE'
            AND EXISTS(SELECT 1 FROM dbo.user_role ur JOIN dbo.role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='GROOM')
            ORDER BY u.full_name,u.id
            """,(r,n)->new Person(r.getLong(1),r.getString(2)));
    }
    private void period(long horse,long groom,LocalDate from,LocalDate to){
        if(from==null||to==null||from.isBefore(calendar.today())||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>365)
            throw error(ErrorCode.VALIDATION_ERROR,"Choose today or later and at most 366 days per plan.");
        if(count("SELECT COUNT(*) FROM dbo.horse WHERE id=?",horse)!=1)throw error(ErrorCode.NOT_FOUND,"Horse was not found.");
        if(grooms().stream().noneMatch(g->g.id()==groom))throw error(ErrorCode.VALIDATION_ERROR,"Choose an ACTIVE Groom.");
        var coverage=jdbc.query("SELECT effective_from,effective_to FROM dbo.horse_stable_assignment WHERE horse_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>=?)",
            (r,n)->new Period(r.getObject(1,LocalDate.class),r.getObject(2,LocalDate.class)),horse,to,from);
        for(var day:from.datesUntil(to.plusDays(1)).toList())
            if(coverage.stream().filter(p->!day.isBefore(p.from())&&(p.to()==null||!day.isAfter(p.to()))).count()!=1)
                throw error(ErrorCode.CONFLICT,"Horse must have exactly one stable assignment on every task date.");
    }
    private record Period(LocalDate from,LocalDate to){}
    @Transactional(readOnly=true) public List<Plan> plans(long horseId,boolean nutrition){
        readCare();String table=nutrition?"nutrition_plan":"care_plan";
        return jdbc.query("SELECT p.id FROM dbo."+table+" p WHERE p.horse_id=? ORDER BY p.effective_from DESC,p.id DESC",
            (r,n)->r.getLong(1),horseId).stream().map(id->plan(id,nutrition)).toList();
    }
    private Plan plan(long id,boolean nutrition){
        String source=nutrition?"nutrition_plan_item":"care_plan_item", column=nutrition?"nutrition_plan_item_id":"care_plan_item_id";
        String table=nutrition?"nutrition_plan":"care_plan", notes=nutrition?"feeding_notes":"notes";
        String end=nutrition?"(SELECT MAX(t.task_date) FROM dbo.care_task t JOIN dbo.nutrition_plan_item i ON i.id=t.nutrition_plan_item_id WHERE i.nutrition_plan_id=p.id)":"p.effective_to";
        var rows=jdbc.query("SELECT p.id,p.horse_id,h.name,p.effective_from,"+end+",p."+notes+
            ",(SELECT MIN(t.assigned_groom_user_id) FROM dbo.care_task t JOIN dbo."+source+" i ON i.id=t."+column+" WHERE i."+table+"_id=p.id) FROM dbo."+table+" p JOIN dbo.horse h ON h.id=p.horse_id WHERE p.id=?",
            (r,n)->new Plan(r.getLong(1),r.getLong(2),r.getString(3),r.getObject(4,LocalDate.class),r.getObject(5,LocalDate.class),r.getString(6),r.getObject(7,Long.class),List.of(),List.of()),id);
        if(rows.isEmpty())throw error(ErrorCode.NOT_FOUND,"Plan was not found.");
        var p=rows.getFirst();
        var care=nutrition?List.<CareItem>of():jdbc.query("SELECT task_type,time_slot,instructions FROM dbo.care_plan_item WHERE care_plan_id=? ORDER BY id",(r,n)->new CareItem(r.getString(1),r.getString(2),r.getString(3)),id);
        var food=nutrition?jdbc.query("SELECT meal,component,quantity,unit FROM dbo.nutrition_plan_item WHERE nutrition_plan_id=? ORDER BY id",(r,n)->new NutritionItem(r.getString(1),r.getString(2),r.getBigDecimal(3),r.getString(4)),id):List.<NutritionItem>of();
        return new Plan(p.id(),p.horseId(),p.horseName(),p.from(),p.to(),p.notes(),p.groomUserId(),care,food);
    }
    private void editable(long id,long horse,boolean nutrition){
        var p=plan(id,nutrition);
        if(p.horseId()!=horse)throw error(ErrorCode.CONFLICT,"A plan cannot move to another horse.");
        String item=nutrition?"nutrition_plan_item":"care_plan_item";
        String plan=nutrition?"nutrition_plan":"care_plan";
        if(count("SELECT COUNT(*) FROM dbo.care_task t JOIN dbo."+item+" i ON i.id=t."+item+"_id WHERE i."+plan+"_id=? AND (t.completed_at IS NOT NULL OR t.task_date<?)",id,calendar.today())!=0)
            throw error(ErrorCode.CONFLICT,"This plan has historical or completed tasks. Preserve its log and create a non-overlapping future plan.");
        if(count("SELECT COUNT(*) FROM dbo.care_task_change ch JOIN dbo.care_task t ON t.id=ch.care_task_id JOIN dbo."+item+" i ON i.id=t."+item+"_id WHERE i."+plan+"_id=?",id)>0)
            throw error(ErrorCode.CONFLICT,"This plan has recorded task changes. Manage pending tasks individually to preserve history.");
        jdbc.update("DELETE FROM dbo.care_task WHERE "+item+"_id IN (SELECT id FROM dbo."+item+" WHERE "+plan+"_id=?)",id);
        jdbc.update("DELETE FROM dbo."+item+" WHERE "+plan+"_id=?",id);
    }
    @Transactional(isolation=Isolation.SERIALIZABLE) public Plan care(Long id,CarePlanRequest r){
        var actor=require(RoleCode.CLUB_MANAGER);period(r.horseId(),r.groomUserId(),r.from(),r.to());
        if(id!=null)editable(id,r.horseId(),false);
        var distinct=new HashSet<String>();
        for(var i:r.items()){
            if(!distinct.add(i.taskType()+"/"+i.timeSlot().trim()))throw error(ErrorCode.VALIDATION_ERROR,"Duplicate task type and slot in this plan.");
            if(count("SELECT COUNT(*) FROM dbo.care_task t JOIN dbo.care_plan_item i ON i.id=t.care_plan_item_id WHERE t.horse_id=? AND t.task_date BETWEEN ? AND ? AND i.task_type=? AND t.scheduled_slot=?",r.horseId(),r.from(),r.to(),i.taskType(),i.timeSlot().trim())>0)
                throw error(ErrorCode.CONFLICT,"An overlapping task already exists for this horse, type and slot.");
        }
        if(id==null)id=insert("INSERT INTO dbo.care_plan(horse_id,managed_by_user_id,effective_from,effective_to,notes) VALUES(?,?,?,?,?)",r.horseId(),actor.userId(),r.from(),r.to(),r.notes());
        else jdbc.update("UPDATE dbo.care_plan SET effective_from=?,effective_to=?,notes=?,updated_at=? WHERE id=?",r.from(),r.to(),r.notes(),now(),id);
        for(var i:r.items()){
            long item=insert("INSERT INTO dbo.care_plan_item(care_plan_id,task_type,time_slot,frequency,responsible_role,instructions) VALUES(?,?,?,'DAILY','GROOM',?)",id,i.taskType(),i.timeSlot().trim(),i.instructions().trim());
            generate(r.horseId(),r.groomUserId(),r.from(),r.to(),item,false,i.timeSlot().trim(),i.instructions().trim());
        }
        audit(actor,"CARE_PLAN_SAVED","CARE_PLAN",id,"Daily care tasks generated for the explicit date range.");notifyGroom(r.groomUserId(),id);
        return plan(id,false);
    }
    @Transactional(isolation=Isolation.SERIALIZABLE) public Plan nutrition(Long id,NutritionRequest r){
        var actor=require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER);period(r.horseId(),r.groomUserId(),r.from(),r.to());
        if(id!=null)editable(id,r.horseId(),true);
        if(count("SELECT COUNT(*) FROM dbo.care_task WHERE horse_id=? AND nutrition_plan_item_id IS NOT NULL AND task_date BETWEEN ? AND ?",r.horseId(),r.from(),r.to())>0)
            throw error(ErrorCode.CONFLICT,"A nutrition plan already covers this horse in the selected period.");
        var distinct=new HashSet<String>();
        for(var i:r.items())if(!distinct.add(i.meal().trim()+"/"+i.component()))throw error(ErrorCode.VALIDATION_ERROR,"Duplicate meal and component.");
        if(id==null)id=insert("INSERT INTO dbo.nutrition_plan(horse_id,maintained_by_user_id,effective_from,feeding_notes) VALUES(?,?,?,?)",r.horseId(),actor.userId(),r.from(),r.notes());
        else jdbc.update("UPDATE dbo.nutrition_plan SET effective_from=?,feeding_notes=?,updated_at=? WHERE id=?",r.from(),r.notes(),now(),id);
        for(var i:r.items()){
            long item=insert("INSERT INTO dbo.nutrition_plan_item(nutrition_plan_id,meal,component,quantity,unit) VALUES(?,?,?,?,?)",id,i.meal().trim(),i.component(),i.quantity(),i.unit().trim());
            generate(r.horseId(),r.groomUserId(),r.from(),r.to(),item,true,i.meal().trim(),i.component()+": "+i.quantity().toPlainString()+" "+i.unit().trim());
        }
        audit(actor,"NUTRITION_PLAN_SAVED","NUTRITION_PLAN",id,"Feeding tasks generated; completion does not deduct stock.");notifyGroom(r.groomUserId(),id);
        return plan(id,true);
    }
    private void generate(long horse,long groom,LocalDate from,LocalDate to,long item,boolean nutrition,String slot,String instruction){
        // Explicit bounded generation: at most 366 days x 20 items. GET requests never create tasks.
        for(var date:from.datesUntil(to.plusDays(1)).toList())jdbc.update("INSERT INTO dbo.care_task(horse_id,"+(nutrition?"nutrition_plan_item_id":"care_plan_item_id")+",assigned_groom_user_id,task_date,scheduled_slot,instruction_snapshot) VALUES(?,?,?,?,?,?)",horse,item,groom,date,slot,instruction);
    }
    private void notifyGroom(long groom,long id){notifications.persistAndPublish(groom,Category.SCHEDULE,"STABLE_CARE_ASSIGNED","Công việc chăm sóc mới","Lịch chăm sóc hoặc khẩu phần đã cập nhật. Xem công việc theo ngày.","STABLE_CARE",id,false);}
    @Transactional(readOnly=true) public List<Task> tasks(LocalDate date){
        var actor=require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN,RoleCode.GROOM);
        if(date==null)throw error(ErrorCode.VALIDATION_ERROR,"date is required.");
        return queryTasks(" AND t.task_date=?"+(actor.roles().contains(RoleCode.GROOM)?" AND t.assigned_groom_user_id=?":""),actor.roles().contains(RoleCode.GROOM)?new Object[]{date,actor.userId()}:new Object[]{date});
    }
    @Transactional(readOnly=true) public DailySummary summary(LocalDate date){var tasks=tasks(date);int done=(int)tasks.stream().filter(t->t.completedAt()!=null).count();int cancelled=(int)tasks.stream().filter(t->t.cancelledAt()!=null).count();return new DailySummary(date,tasks.size(),done,tasks.size()-done-cancelled,cancelled);}
    @Transactional public Task complete(long id){
        var actor=require(RoleCode.GROOM);
        // Lock the same existing task row for completion, cancellation and reassignment on both SQL Server and H2.
        if(jdbc.update("UPDATE dbo.care_task SET assigned_groom_user_id=assigned_groom_user_id WHERE id=? AND assigned_groom_user_id=? AND treatment_plan_id IS NULL",id,actor.userId())==0)
            throw error(ErrorCode.NOT_FOUND,"Assigned daily-care task was not found.");
        var rows=queryTasks(" AND t.id=? AND t.assigned_groom_user_id=?",id,actor.userId());
        if(rows.isEmpty())throw error(ErrorCode.NOT_FOUND,"Assigned daily-care task was not found.");
        var task=rows.getFirst();if(task.taskDate().isAfter(calendar.today()))throw error(ErrorCode.CONFLICT,"Future tasks cannot be completed early.");
        if(task.cancelledAt()!=null)throw error(ErrorCode.CONFLICT,"This task is cancelled and cannot be completed.");
        if(task.completedAt()!=null)return task;
        if(jdbc.update("UPDATE dbo.care_task SET completed_by_user_id=?,completed_at=?,updated_at=? WHERE id=? AND assigned_groom_user_id=? AND completed_at IS NULL",actor.userId(),now(),now(),id,actor.userId())==1)
            audit(actor,"DAILY_CARE_COMPLETED","CARE_TASK",id,"Daily task completed; no stock, precheck or medical restriction changed.");
        return queryTasks(" AND t.id=? AND t.assigned_groom_user_id=?",id,actor.userId()).stream().findFirst()
            .orElseThrow(()->error(ErrorCode.CONFLICT,"The plan changed while completing this task. Refresh your assignments and review before trying again."));
    }
    private Task task(long id){return queryTasks(" AND t.id=?",id).stream().findFirst().orElseThrow(()->error(ErrorCode.NOT_FOUND,"Daily-care task was not found."));}
    private ActorContext taskWriter(Task task){return "NUTRITION".equals(task.source())?require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER):require(RoleCode.CLUB_MANAGER);}
    private Task lockPending(long id,long expectedGroom){
        if(jdbc.update("UPDATE dbo.care_task SET assigned_groom_user_id=assigned_groom_user_id WHERE id=? AND assigned_groom_user_id=? AND treatment_plan_id IS NULL",id,expectedGroom)==0)
            throw error(ErrorCode.CONFLICT,"Task assignment changed. Refresh before trying again.");
        var current=task(id);
        if(current.completedAt()!=null||current.taskDate().isBefore(calendar.today()))
            throw error(ErrorCode.CONFLICT,"Completed or past tasks cannot be cancelled or reassigned.");
        return current;
    }
    @Transactional public Task reassign(long id,ReassignTaskRequest r){
        var actor=taskWriter(task(id));var current=lockPending(id,r.expectedGroomUserId());
        if(current.cancelledAt()!=null)throw error(ErrorCode.CONFLICT,"Cancelled tasks cannot be reassigned.");
        if(grooms().stream().noneMatch(g->g.id()==r.groomUserId()))throw error(ErrorCode.VALIDATION_ERROR,"Choose an ACTIVE Groom.");
        if(current.groomUserId()==r.groomUserId())return current;
        jdbc.update("UPDATE dbo.care_task SET assigned_groom_user_id=?,updated_at=? WHERE id=?",r.groomUserId(),now(),id);
        change(actor,current,"REASSIGN",r.groomUserId(),r.reason());
        notifyChange(current.groomUserId(),id,"Công việc đã đổi người phụ trách");notifyChange(r.groomUserId(),id,"Bạn được giao công việc chăm sóc");
        return task(id);
    }
    @Transactional public Task cancel(long id,CancelTaskRequest r){
        var actor=taskWriter(task(id));var current=lockPending(id,r.expectedGroomUserId());
        if(current.cancelledAt()!=null)return current;
        change(actor,current,"CANCEL",null,r.reason());notifyChange(current.groomUserId(),id,"Công việc chăm sóc đã được hủy");return task(id);
    }
    private void change(ActorContext actor,Task task,String type,Long newGroom,String reason){
        long change=insert("INSERT INTO dbo.care_task_change(care_task_id,change_type,previous_groom_user_id,new_groom_user_id,changed_by_user_id,changed_at,reason) VALUES(?,?,?,?,?,?,?)",task.id(),type,task.groomUserId(),newGroom,actor.userId(),now(),reason.trim());
        audit(actor,"DAILY_CARE_"+(type.equals("CANCEL")?"CANCELLED":"REASSIGNED"),"CARE_TASK",task.id(),"Task change history recorded as "+change+"; completed work, medical restrictions and stock remain unchanged.");
    }
    private void notifyChange(long user,long task,String title){notifications.persistAndPublish(user,Category.SCHEDULE,"STABLE_CARE_CHANGED",title,"Phân công đã cập nhật. Xem danh sách công việc theo ngày.","STABLE_CARE",task,false);}
    @Transactional(readOnly=true) public List<TaskChange> changes(long id){
        var actor=require(RoleCode.CLUB_MANAGER,RoleCode.HEAD_TRAINER,RoleCode.VETERINARIAN,RoleCode.GROOM);var current=task(id);
        if(actor.roles().contains(RoleCode.GROOM)&&current.groomUserId()!=actor.userId())throw error(ErrorCode.NOT_FOUND,"Assigned daily-care task was not found.");
        return jdbc.query("SELECT ch.id,ch.care_task_id,ch.change_type,ch.previous_groom_user_id,p.full_name,ch.new_groom_user_id,n.full_name,a.full_name,ch.changed_at,ch.reason FROM dbo.care_task_change ch JOIN dbo.app_user p ON p.id=ch.previous_groom_user_id LEFT JOIN dbo.app_user n ON n.id=ch.new_groom_user_id JOIN dbo.app_user a ON a.id=ch.changed_by_user_id WHERE ch.care_task_id=? ORDER BY ch.id DESC",
            (rs,num)->new TaskChange(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getObject(6,Long.class),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant(),rs.getString(10)),id);
    }
    private List<Task> queryTasks(String filter,Object...args){return jdbc.query("""
        SELECT t.id,t.horse_id,h.name,h.horse_code,CASE WHEN t.nutrition_plan_item_id IS NULL THEN 'CARE' ELSE 'NUTRITION' END source,
        COALESCE(i.task_type,'FEEDING') task_type,t.task_date,t.scheduled_slot,t.instruction_snapshot,t.assigned_groom_user_id,g.full_name,
        (SELECT MIN(b.box_code) FROM dbo.horse_stable_assignment a JOIN dbo.stable_box b ON b.id=a.stable_box_id
         WHERE a.horse_id=t.horse_id AND a.effective_from<=t.task_date AND (a.effective_to IS NULL OR a.effective_to>=t.task_date)) box_code,t.completed_at,cancelled.changed_at,cancelled.reason
        FROM dbo.care_task t JOIN dbo.horse h ON h.id=t.horse_id JOIN dbo.app_user g ON g.id=t.assigned_groom_user_id
        LEFT JOIN dbo.care_plan_item i ON i.id=t.care_plan_item_id
        LEFT JOIN dbo.care_task_change cancelled ON cancelled.care_task_id=t.id AND cancelled.change_type='CANCEL'
        WHERE t.treatment_plan_id IS NULL AND (t.care_plan_item_id IS NOT NULL OR t.nutrition_plan_item_id IS NOT NULL)
        """+filter+" ORDER BY t.task_date,t.scheduled_slot,h.name,t.id",(r,n)->new Task(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getObject(7,LocalDate.class),r.getString(8),r.getString(9),r.getLong(10),r.getString(11),r.getString(12),r.getTimestamp(13)==null?null:r.getTimestamp(13).toInstant(),r.getTimestamp(14)==null?null:r.getTimestamp(14).toInstant(),r.getString(15)),args);}
    @Transactional(readOnly=true) public List<StockItem> inventory(){
        require(RoleCode.CLUB_MANAGER,RoleCode.GROOM,RoleCode.VETERINARIAN);
        return jdbc.query("SELECT id,name,category,unit,threshold,current_quantity,is_active FROM dbo.inventory_item ORDER BY name,id",(r,n)->new StockItem(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6),r.getBoolean(7)));
    }
    private StockItem item(long id){return inventory().stream().filter(i->i.id()==id).findFirst().orElseThrow(()->error(ErrorCode.NOT_FOUND,"Inventory item was not found."));}
    @Transactional(isolation=Isolation.SERIALIZABLE) public StockItem saveItem(Long id,StockItemRequest r){
        var actor=require(RoleCode.CLUB_MANAGER);
        if(count("SELECT COUNT(*) FROM dbo.inventory_item WHERE name=? AND unit=? AND id<>?",r.name().trim(),r.unit().trim(),id==null?0:id)>0)throw error(ErrorCode.CONFLICT,"An inventory item with this name and unit already exists.");
        if(id==null)id=insert("INSERT INTO dbo.inventory_item(name,category,unit,threshold,is_active) VALUES(?,?,?,?,?)",r.name().trim(),r.category(),r.unit().trim(),r.threshold(),r.active());
        else {
            var old=item(id);if(!old.unit().equals(r.unit().trim())&&count("SELECT COUNT(*) FROM dbo.stock_movement WHERE inventory_item_id=?",id)>0)throw error(ErrorCode.CONFLICT,"Units cannot change after stock movements.");
            jdbc.update("UPDATE dbo.inventory_item SET name=?,category=?,unit=?,threshold=?,is_active=?,updated_at=? WHERE id=?",r.name().trim(),r.category(),r.unit().trim(),r.threshold(),r.active(),now(),id);
        }
        audit(actor,"INVENTORY_ITEM_SAVED","INVENTORY_ITEM",id,"Inventory metadata saved; quantity changes require an explicit stock movement.");return item(id);
    }
    @Transactional public StockItem move(long id,MovementRequest r){
        var actor=require(RoleCode.CLUB_MANAGER);item(id);
        if(r.changeQuantity().signum()==0||r.changeQuantity().abs().compareTo(new java.math.BigDecimal("999999999.999"))>0)throw error(ErrorCode.VALIDATION_ERROR,"Stock movement must be nonzero and within the supported precision.");
        if(jdbc.update("UPDATE dbo.inventory_item SET current_quantity=current_quantity+?,updated_at=? WHERE id=? AND is_active=1 AND current_quantity=? AND current_quantity+?>=0 AND current_quantity+?<=999999999.999",r.changeQuantity(),now(),id,r.expectedQuantity(),r.changeQuantity(),r.changeQuantity())!=1)
            throw error(ErrorCode.CONFLICT,"Stock changed, item is inactive, or quantity is insufficient. Refresh and review before submitting again.");
        long movement=insert("INSERT INTO dbo.stock_movement(inventory_item_id,recorded_by_user_id,change_quantity,note,recorded_at) VALUES(?,?,?,?,?)",id,actor.userId(),r.changeQuantity(),r.note().trim(),now());
        audit(actor,"STOCK_MOVEMENT_RECORDED","STOCK_MOVEMENT",movement,"Manual stock movement recorded atomically with the balance.");return item(id);
    }
    @Transactional(readOnly=true) public List<Movement> movements(long id){
        require(RoleCode.CLUB_MANAGER,RoleCode.GROOM,RoleCode.VETERINARIAN);item(id);
        return jdbc.query("SELECT m.id,m.inventory_item_id,i.name,m.change_quantity,i.unit,m.note,u.full_name,m.recorded_at FROM dbo.stock_movement m JOIN dbo.inventory_item i ON i.id=m.inventory_item_id JOIN dbo.app_user u ON u.id=m.recorded_by_user_id WHERE m.inventory_item_id=? ORDER BY m.recorded_at DESC,m.id DESC",(r,n)->new Movement(r.getLong(1),r.getLong(2),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getString(6),r.getString(7),r.getTimestamp(8).toInstant()),id);
    }
    @Transactional(readOnly=true) public List<Replenishment> requests(){
        var actor=require(RoleCode.CLUB_MANAGER,RoleCode.GROOM);
        return jdbc.query("SELECT q.id,q.inventory_item_id,i.name,q.quantity,i.unit,q.reason,u.full_name,q.submitted_at FROM dbo.replenishment_request q JOIN dbo.inventory_item i ON i.id=q.inventory_item_id JOIN dbo.app_user u ON u.id=q.requested_by_user_id"+(actor.roles().contains(RoleCode.CLUB_MANAGER)?"":" WHERE q.requested_by_user_id=?")+" ORDER BY q.submitted_at DESC,q.id DESC",
            (r,n)->new Replenishment(r.getLong(1),r.getLong(2),r.getString(3),r.getBigDecimal(4),r.getString(5),r.getString(6),r.getString(7),r.getTimestamp(8).toInstant()),actor.roles().contains(RoleCode.CLUB_MANAGER)?new Object[]{}:new Object[]{actor.userId()});
    }
    @Transactional public Replenishment request(ReplenishmentRequest r){
        var actor=require(RoleCode.GROOM);if(!item(r.inventoryItemId()).active())throw error(ErrorCode.CONFLICT,"Inventory item is inactive.");
        long id=insert("INSERT INTO dbo.replenishment_request(inventory_item_id,requested_by_user_id,quantity,reason,submitted_at) VALUES(?,?,?,?,?)",r.inventoryItemId(),actor.userId(),r.quantity(),r.reason().trim(),now());
        audit(actor,"REPLENISHMENT_REQUESTED","REPLENISHMENT_REQUEST",id,"Groom submitted a replenishment request; stock was not changed.");
        return requests().stream().filter(q->q.id()==id).findFirst().orElseThrow();
    }
    private long insert(String sql,Object...args){var keys=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int n=0;n<args.length;n++)p.setObject(n+1,args[n]);return p;},keys);return GeneratedIdentity.requireId(keys);}
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private Timestamp now(){return Timestamp.from(calendar.now());}
    private void audit(ActorContext actor,String action,String target,long id,String summary){
        String role=actor.roles().contains(RoleCode.CLUB_MANAGER)?"CLUB_MANAGER":actor.roles().contains(RoleCode.HEAD_TRAINER)?"HEAD_TRAINER":"GROOM";
        jdbc.update("INSERT INTO dbo.audit_log(actor_user_id,actor_role,action,target_type,target_id,safe_summary,source) VALUES(?,?,?,?,?,?,'WEB')",actor.userId(),role,action,target,id,summary);
    }
    private static BusinessException error(ErrorCode code,String text){return new BusinessException(code,text);}
}
