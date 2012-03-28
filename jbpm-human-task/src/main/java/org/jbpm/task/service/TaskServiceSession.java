/**
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.task.service;

import org.drools.RuleBase;
import org.drools.StatefulSession;
import org.jbpm.eventmessaging.EventKeys;
import org.jbpm.task.*;
import org.jbpm.task.query.DeadlineSummary;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.TaskService.ScheduledTaskDeadline;

import javax.persistence.*;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskServiceSession {

    private final TaskService service;
    private final EntityManager em;
    private Map<String, RuleBase> ruleBases;
    private Map<String, Map<String, Object>> globals;
    private EventKeys eventKeys;

    public TaskServiceSession(final TaskService service, final EntityManager em) {
        this.service = service;
        this.em = em;
    }

    public void dispose() {
        em.close();
    }

    public EntityManager getEntityManager() {
        return em;
    }

    public void setRuleBase(final String type, final RuleBase ruleBase) {
        if (ruleBases == null) {
            ruleBases = new HashMap<String, RuleBase>();
        }
        ruleBases.put(type, ruleBase);
    }

    public void setGlobals(final String type, final Map<String, Object> globals) {
        if (this.globals == null) {
            this.globals = new HashMap<String, Map<String, Object>>();
        }
        this.globals.put(type, globals);
    }

    public void addUser(final User user) {
        persistInTransaction(user);
    }

    public void addGroup(final Group group) {
        persistInTransaction(group);
    }

    /**
     * Runs any custom rules against the specified Task and ContentData to ensure that the
     * task is allowed to be added. If the task cannot be added, a <code>CannotAddTaskException</code>
     * will be thrown.
     * @param task task that is being added
     * @param contentData content data for task
     * @throws CannotAddTaskException throw if the task is not allowed to be added
     */
    private void executeTaskAddRules(final Task task, final ContentData contentData)
        throws CannotAddTaskException
    {
        RuleBase ruleBase = ruleBases.get("addTask");
        if (ruleBase != null) {
            StatefulSession session = ruleBase.newStatefulSession();
            Map<String, Object> globals = this.globals.get("addTask");
            if (globals != null) {
                for (Map.Entry<String, Object> entry : globals.entrySet()) {
                    session.setGlobal(entry.getKey(), entry.getValue());
                }
            }
            TaskServiceRequest request = new TaskServiceRequest("addTask", null, null);
            session.setGlobal("request", request);
            session.insert(task);
            session.insert(contentData);
            session.fireAllRules();

            if (!request.isAllowed()) {
                StringBuilder error = new StringBuilder("Cannot add Task:\n");
                if (request.getReasons() != null) {
                    for (String reason : request.getReasons()) {
                        error.append( reason).append('\n');
                    }
                }

                throw new CannotAddTaskException(error.toString());
            }
        }
    }

    public void addTask(final Task task, final ContentData contentData)
        throws CannotAddTaskException
    {
        final TaskData taskData = task.getTaskData();

        // initialize the task data
        Status currentStatus = taskData.initialize();

        if (ruleBases != null) {
            executeTaskAddRules(task, contentData);
        }

        // than assign the TaskData an owner and status based on the task assignments
        PeopleAssignments assignments = task.getPeopleAssignments();
        if (assignments != null) {
            List<OrganizationalEntity> potentialOwners = assignments.getPotentialOwners();
            currentStatus = taskData.assignOwnerAndStatus(potentialOwners);
        }

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                em.persist(task);

                if (contentData != null) {
                    Content content = new Content(contentData.getContent());
                    em.persist(content);

                    taskData.setDocument(content.getId(), contentData);
                }
            }
        });

        // schedule after it's been persisted, otherwise the id's won't be assigned
        if (task.getDeadlines() != null) {
            scheduleTask(task);
        }

        if (currentStatus == Status.Reserved) {
            // Task was reserved so owner should get icals
            SendIcal.getInstance().sendIcalForTask(task, service.getUserinfo());

            // trigger event support
            service.getEventSupport().fireTaskClaimed(task.getId(), task.getTaskData().getActualOwner().getId());
        }
    }

    private void scheduleTask(final Task task) {
        final long now = System.currentTimeMillis();

        final List<Deadline> startDeadlines = task.getDeadlines().getStartDeadlines();

        if (startDeadlines != null) {
            scheduleDeadlines(startDeadlines, now, task.getId());
        }

        final List<Deadline> endDeadlines = task.getDeadlines().getEndDeadlines();

        if (endDeadlines != null) {
            scheduleDeadlines(endDeadlines, now, task.getId());
        }
    }

    private void scheduleDeadlines(final List<Deadline> deadlines, final long now, final long taskId) {
        for (Deadline deadline : deadlines) {
            if (!deadline.isEscalated()) {
                // only escalate when true - typically this would only be true
                // if the user is requested that the notification should never be escalated
                Date date = deadline.getDate();
                service.schedule(new ScheduledTaskDeadline(taskId, deadline.getId(), service), date.getTime() - now);
            }
        }
    }

    void evalCommand(final Operation operation, final List<OperationCommand> commands, final Task task,
                     final User user, final OrganizationalEntity targetEntity,
                     List<String> groupIds) throws PermissionDeniedException {

        final TaskData taskData = task.getTaskData();
        boolean statusMatched = false;

        for (OperationCommand command : commands) {
            // first find out if we have a matching status
            if (command.getStatus() != null) {
                for (Status status : command.getStatus()) {
                    if (taskData.getStatus() == status) {
                        statusMatched = true;

                        // next find out if the user can execute this doOperation
                        if (!isAllowed(command, task, user, groupIds)) {
                            String errorMessage = "User '" + user + "' does not have permissions to execution operation '" + operation + "' on task id " + task.getId();

                            throw new PermissionDeniedException(errorMessage);
                        }

                        commands(command, task, user, targetEntity);
                    }
                }
            }

            if (command.getPreviousStatus() != null) {
                for (Status status : command.getPreviousStatus()) {
                    if (taskData.getPreviousStatus() == status) {
                        statusMatched = true;

                        // next find out if the user can execute this doOperation
                        if (!isAllowed(command, task, user, groupIds)) {
                            String errorMessage = "User '" + user + "' does not have permissions to execution operation '" + operation + "' on task id " + task.getId();
                            throw new PermissionDeniedException(errorMessage);
                        }

                        commands(command, task, user, targetEntity);
                    }
                }
            }
        }
        if (!statusMatched) {
            String errorMessage = "User '" + user + "' was unable to execution operation '" + operation + "' on task id " + task.getId() + " due to no 'current status' matchines";
            throw new PermissionDeniedException(errorMessage);
        }
    }

    private static boolean isAllowed(final OperationCommand command, final Task task, final User user,
    		                         final List<String> groupIds) {
        final PeopleAssignments people = task.getPeopleAssignments();
        final TaskData taskData = task.getTaskData();

        boolean operationAllowed = false;
        for (Allowed allowed : command.getAllowed()) {
            if (operationAllowed) {
                break;
            }
            switch (allowed) {
                case Owner: {
                    operationAllowed = (taskData.getActualOwner() != null && taskData.getActualOwner().equals(user));
                    break;
                }
                case Initiator: {
                    operationAllowed = (taskData.getCreatedBy() != null && 
                		(taskData.getCreatedBy().equals(user)) 
                		 || (groupIds != null && groupIds.contains(taskData.getCreatedBy().getId())));
                    break;
                }
                case PotentialOwner: {
                    operationAllowed = isAllowed(user, groupIds, people.getPotentialOwners());
                    break;
                }
                case BusinessAdministrator: {
                    operationAllowed = isAllowed(user, groupIds, people.getBusinessAdministrators());
                    break;
                }
                case Anyone: {
                	operationAllowed = true;
                	break;
                }
            }
        }

        if (operationAllowed && command.isUserIsExplicitPotentialOwner()) {
            // if user has rights to execute the command, make sure user is explicitely specified (not as a group)
            operationAllowed = people.getPotentialOwners().contains(user);
        }

        if (operationAllowed && command.isSkippable()) {
            operationAllowed = taskData.isSkipable();
        }

        return operationAllowed;
    }

    private void commands(final OperationCommand command, final Task task, final User user,
                          final OrganizationalEntity targetEntity) {
        final PeopleAssignments people = task.getPeopleAssignments();
        final TaskData taskData = task.getTaskData();

        if (command.getNewStatus() != null) {
            taskData.setStatus(command.getNewStatus());
        } else if (command.isSetToPreviousStatus()) {
            taskData.setStatus(taskData.getPreviousStatus());
        }

        if (command.isAddTargetEntityToPotentialOwners() && !people.getPotentialOwners().contains(targetEntity)) {
            people.getPotentialOwners().add(targetEntity);
        }

        if (command.isRemoveUserFromPotentialOwners()) {
            people.getPotentialOwners().remove(user);
        }

        if (command.isSetNewOwnerToUser()) {
            taskData.setActualOwner(user);
        }

        if (command.isSetNewOwnerToNull()) {
            taskData.setActualOwner(null);
        }

        if (command.getExec() != null) {
            switch (command.getExec()) {
                case Claim: {
                    taskData.setActualOwner((User) targetEntity);
                    // Task was reserved so owner should get icals
                    SendIcal.getInstance().sendIcalForTask(task, service.getUserinfo());

                    // trigger event support
                    service.getEventSupport().fireTaskClaimed(task.getId(),
                            task.getTaskData().getActualOwner().getId());
                    break;
                }
            }
        }
    }

    public void taskOperation(final Operation operation, final long taskId, final String userId,
                              final String targetEntityId, final ContentData data,
                              List<String> groupIds) throws TaskException {
        OrganizationalEntity targetEntity = null;

        if (targetEntityId != null) {
            targetEntity = getEntity(OrganizationalEntity.class, targetEntityId);
        }

        final Task task = getTask(taskId);
        final User user = getEntity(User.class, userId);

        try {
            final List<OperationCommand> commands = service.getCommandsForOperation(operation);

            beginOrUseExistingTransaction();

            evalCommand(operation, commands, task, user, targetEntity, groupIds);

            switch (operation) {
                case Claim: {
                    taskClaimOperation(task);
                    break;
                }
                case Complete: {
                    taskCompleteOperation(task, data);
                    break;
                }
                case Fail: {
                    taskFailOperation(task, data);
                    break;
                }
                case Skip: {
                    taskSkipOperation(task, userId);
                    break;
                }
                case Remove: {
                	taskRemoveOperation(task, user);
                	break;
                }
                case Register: {
                	taskRegisterOperation(task, user);
                	break;
                }
            }
        } catch (RuntimeException e) {
            em.getTransaction().rollback();

            doOperationInTransaction(new TransactedOperation() {
                public void doOperation() {
                    task.getTaskData().setStatus(Status.Error);
                }
            });

            throw e;
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().commit();
            }
        }
    }

    private void taskClaimOperation(final Task task) {
        // Task was reserved so owner should get icals
        SendIcal.getInstance().sendIcalForTask(task, service.getUserinfo());
        // trigger event support
        service.getEventSupport().fireTaskClaimed(task.getId(), task.getTaskData().getActualOwner().getId());
    }

    private void taskCompleteOperation(final Task task, final ContentData data) {
        if (data != null) {
        	setOutput(task.getId(), task.getTaskData().getActualOwner().getId(), data);
        }

        // trigger event support
        service.getEventSupport().fireTaskCompleted(task.getId(), task.getTaskData().getActualOwner().getId());
        checkSubTaskStrategy(task);
    }

    private void taskFailOperation(final Task task, final ContentData data) {
        // set fault data
        if (data != null) {
        	setFault(task.getId(), task.getTaskData().getActualOwner().getId(), (FaultData) data);
        }

        // trigger event support
        service.getEventSupport().fireTaskFailed(task.getId(), task.getTaskData().getActualOwner().getId());
    }

    private void taskSkipOperation(final Task task, final String userId) {
        // trigger event support
        service.getEventSupport().fireTaskSkipped(task.getId(), userId);
        checkSubTaskStrategy(task);
    }

    public Task getTask(final long taskId) {
        return getEntity(Task.class, taskId);
    }

    public void addComment(final long taskId, final Comment comment) {
        final Task task = getTask(taskId);

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                task.getTaskData().addComment(comment);
            }
        });
    }

    public void addAttachment(final long taskId, final Attachment attachment, final Content content) {
        final Task task = getTask(taskId);

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                em.persist(content);

                attachment.setContent(content);
                task.getTaskData().addAttachment(attachment);
            }
        });
    }

    public void setDocumentContent(final long taskId, final Content content) {
        final Task task = getTask(taskId);

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                em.persist(content);

                task.getTaskData().setDocumentContentId(content.getId());
            }
        });
    }

    public Content getContent(final long contentId) {
        return getEntity(Content.class, contentId);
    }

    public void deleteAttachment(final long taskId, final long attachmentId, final long contentId) {
        // @TODO I can't get this to work with HQL deleting the Attachment. Hibernate needs both the item removed from the collection
        // and also the item deleted, so for now have to load the entire Task, I suspect that this is due to using the same EM which 
        // is caching things.
        final Task task = getTask(taskId);

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                final Attachment removedAttachment = task.getTaskData().removeAttachment(attachmentId);

                if (removedAttachment != null) {
                    // need to do this otherwise it just removes the link id, without removing the attachment
                    em.remove(removedAttachment);
                }

                // we do this as HQL to avoid streaming in the entire HQL
                final String deleteContent = "delete from Content c where c.id = :id";
                em.createQuery(deleteContent).setParameter("id", contentId).executeUpdate();
            }
        });
    }

    public void deleteComment(final long taskId, final long commentId) {
        // @TODO I can't get this to work with HQL deleting the Comment. Hibernate needs both the item removed from the collection
        // and also the item deleted, so for now have to load the entire Task, I suspect that this is due to using the same EM which 
        // is caching things.
        final Task task = getTask(taskId);

        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                final Comment removedComment = task.getTaskData().removeComment(commentId);

                if (removedComment != null) {
                    // need to do this otherwise it just removes the link id, without removing the attachment
                    em.remove(removedComment);
                }
            }
        });
    }

    public List<DeadlineSummary> getUnescalatedDeadlines() {
        return (List<DeadlineSummary>) em.createNamedQuery("UnescalatedDeadlines").getResultList();
    }

    public Task getTaskByWorkItemId(final long workItemId) {
        final Query task = em.createNamedQuery("TaskByWorkItemId");
        task.setParameter("workItemId", workItemId);

        return (Task) task.getSingleResult();
    }

    public List<Task> getTasksByProcessInstanceId(final long processInstanceId) {
        final Query tasks = em.createNamedQuery("TasksByProcessInstanceId");
        tasks.setParameter("processInstanceId", processInstanceId);

        return (List<Task>) tasks.getResultList();
    }

    public List<TaskSummary> getTasksOwned(final String userId, final String language) {
        final Query tasksOwned = em.createNamedQuery("TasksOwned");
        tasksOwned.setParameter("userId", userId);
        tasksOwned.setParameter("language", language);

        return (List<TaskSummary>) tasksOwned.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsBusinessAdministrator(final String userId,
                                                                     final String language) {
        final Query tasksAssignedAsBusinessAdministrator = em.createNamedQuery("TasksAssignedAsBusinessAdministrator");
        tasksAssignedAsBusinessAdministrator.setParameter("userId", userId);
        tasksAssignedAsBusinessAdministrator.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsBusinessAdministrator.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsExcludedOwner(final String userId,
                                                             final String language) {
        final Query tasksAssignedAsExcludedOwner = em.createNamedQuery("TasksAssignedAsExcludedOwner");
        tasksAssignedAsExcludedOwner.setParameter("userId", userId);
        tasksAssignedAsExcludedOwner.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsExcludedOwner.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsPotentialOwner(final String userId,
                                                              final String language) {
        final Query tasksAssignedAsPotentialOwner = em.createNamedQuery("TasksAssignedAsPotentialOwner");
        tasksAssignedAsPotentialOwner.setParameter("userId", userId);
        tasksAssignedAsPotentialOwner.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsPotentialOwner.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsPotentialOwner(final String userId, final List<String> groupIds,
                                                              final String language) {
        final Query tasksAssignedAsPotentialOwner = em.createNamedQuery("TasksAssignedAsPotentialOwnerWithGroups");
        tasksAssignedAsPotentialOwner.setParameter("userId", userId);
        tasksAssignedAsPotentialOwner.setParameter("groupIds", groupIds);
        tasksAssignedAsPotentialOwner.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsPotentialOwner.getResultList();
    }


    public List<TaskSummary> getSubTasksAssignedAsPotentialOwner(final long parentId, final String userId,
                                                                 final String language) {
        final Query tasksAssignedAsPotentialOwner = em.createNamedQuery("SubTasksAssignedAsPotentialOwner");
        tasksAssignedAsPotentialOwner.setParameter("parentId", parentId);
        tasksAssignedAsPotentialOwner.setParameter("userId", userId);
        tasksAssignedAsPotentialOwner.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsPotentialOwner.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsPotentialOwnerByGroup(final String groupId,
                                                                     final String language) {
        final Query tasksAssignedAsPotentialOwnerByGroup = em.createNamedQuery("TasksAssignedAsPotentialOwnerByGroup");
        tasksAssignedAsPotentialOwnerByGroup.setParameter("groupId", groupId);
        tasksAssignedAsPotentialOwnerByGroup.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsPotentialOwnerByGroup.getResultList();
    }

    public List<TaskSummary> getSubTasksByParent(final long parentId, final String language) {
        final Query subTaskByParent = em.createNamedQuery("GetSubTasksByParentTaskId");
        subTaskByParent.setParameter("parentId", parentId);
        subTaskByParent.setParameter("language", language);

        return (List<TaskSummary>) subTaskByParent.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsRecipient(final String userId,
                                                         final String language) {
        final Query tasksAssignedAsRecipient = em.createNamedQuery("TasksAssignedAsRecipient");
        tasksAssignedAsRecipient.setParameter("userId", userId);
        tasksAssignedAsRecipient.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsRecipient.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsTaskInitiator(final String userId,
                                                             final String language) {
        final Query tasksAssignedAsTaskInitiator = em.createNamedQuery("TasksAssignedAsTaskInitiator");
        tasksAssignedAsTaskInitiator.setParameter("userId", userId);
        tasksAssignedAsTaskInitiator.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsTaskInitiator.getResultList();
    }

    public List<TaskSummary> getTasksAssignedAsTaskStakeholder(final String userId,
                                                               final String language) {
        final Query tasksAssignedAsTaskStakeholder = em.createNamedQuery("TasksAssignedAsTaskStakeholder");
        tasksAssignedAsTaskStakeholder.setParameter("userId", userId);
        tasksAssignedAsTaskStakeholder.setParameter("language", language);

        return (List<TaskSummary>) tasksAssignedAsTaskStakeholder.getResultList();
    }
    
    public List<?> query(final String qlString, final Integer size, final Integer offset) {
    	final Query genericQuery = em.createQuery(qlString);
    	genericQuery.setMaxResults(size);
    	genericQuery.setFirstResult(offset);
    	return genericQuery.getResultList();
    }
    
    private void taskRemoveOperation(final Task task, final User user) {
		if (task.getPeopleAssignments().getRecipients().contains(user)) {
			task.getPeopleAssignments().getRecipients().remove(user);
		} else {
			throw new RuntimeException("Couldn't remove user " + user.getId() + " since it isn't a notification recipient");
		}
    }
    
    private void taskRegisterOperation(final Task task, final User user) {
		if (!task.getPeopleAssignments().getRecipients().contains(user)) {
			task.getPeopleAssignments().getRecipients().add(user);
		}
    }
    
    public void nominateTask(final long taskId, String userId, final List<OrganizationalEntity> potentialOwners) {
    	final Task task = getEntity(Task.class, taskId);
    	final User user = getEntity(User.class, userId);
    	if (isAllowed(user, null, task.getPeopleAssignments().getBusinessAdministrators())) {
	    	doOperationInTransaction(new TransactedOperation() {
				public void doOperation() {
					task.getTaskData().assignOwnerAndStatus(potentialOwners);
					if (task.getTaskData().getStatus() == Status.Ready) {
						task.getPeopleAssignments().setPotentialOwners(potentialOwners);
					}
				}
	    	});
    	} else {
    		throw new PermissionDeniedException("User " + userId + " is not allowed to perform Nominate on Task " + taskId);
    	}
    }
    
    public void setOutput(final long taskId, final String userId, final ContentData outputContentData) {
    	doOperationInTransaction(new TransactedOperation() {
    		public void doOperation() {
    			Task task = getEntity(Task.class, taskId);
    			if (!userId.equals(task.getTaskData().getActualOwner().getId())) {
    				throw new RuntimeException("User " + userId + " is not the actual owner of the task " + taskId + " and can't perform setOutput");
    			}
    			Content content = new Content();
    			content.setContent(outputContentData.getContent());
    			em.persist(content);
    			task.getTaskData().setOutput(content.getId(), outputContentData);
    		}
    	});
    }
    
    public void setFault(final long taskId, final String userId, final FaultData faultContentData) {
    	doOperationInTransaction(new TransactedOperation() {
    		public void doOperation() {
    			Task task = getEntity(Task.class, taskId);
    			if (!userId.equals(task.getTaskData().getActualOwner().getId())) {
    				throw new RuntimeException("User " + userId + " is not the actual owner of the task " + taskId + " and can't perform setFault");
    			}
    			Content content = new Content();
    			content.setContent(faultContentData.getContent());
    			em.persist(content);
    			task.getTaskData().setFault(content.getId(), faultContentData);
    		}
    	});
    }
    
    public void setPriority(final long taskId, final String userId, final int priority) {
    	doOperationInTransaction(new TransactedOperation() {
    		public void doOperation() {
    			Task task = getEntity(Task.class, taskId);
    			task.setPriority(priority);
    		}
    	});
    }
    
    public void deleteOutput(final long taskId, final String userId) {
    	doOperationInTransaction(new TransactedOperation() {
    		public void doOperation() {
    			Task task = getEntity(Task.class, taskId);
    			if (!userId.equals(task.getTaskData().getActualOwner().getId())) {
    				throw new RuntimeException("User " + userId + " is not the actual owner of the task " + taskId + " and can't perform deleteOutput");
    			}
    			long contentId = task.getTaskData().getOutputContentId();
    			Content content = getEntity(Content.class, contentId);
    			ContentData data = new ContentData();
    			em.remove(content);
    			task.getTaskData().setOutput(0, data);
    		}
    	});
    }
    
    public void deleteFault(final long taskId, final String userId) {
    	doOperationInTransaction(new TransactedOperation() {
    		public void doOperation() {
    			Task task = getEntity(Task.class, taskId);
    			if (!userId.equals(task.getTaskData().getActualOwner().getId())) {
    				throw new RuntimeException("User " + userId + " is not the actual owner of the task " + taskId + " and can't perform deleteFault");
    			}
    			long contentId = task.getTaskData().getFaultContentId();
    			Content content = getEntity(Content.class, contentId);
    			FaultData data = new FaultData();
    			em.remove(content);
    			task.getTaskData().setFault(0, data);
    		}
    	});
    }
    
    public static boolean isAllowed(final User user, final List<OrganizationalEntity>[] people) {
        for (List<OrganizationalEntity> list : people) {
            if (isAllowed(user, null, list)) {
                return true;
            }
        }
        return false;
    }

    static boolean isAllowed(final User user, final List<String> groupIds, final List<OrganizationalEntity> entities) {
        // for now just do a contains, I'll figure out group membership later.
        for (OrganizationalEntity entity : entities) {
            if (entity instanceof User && entity.equals(user)) {
                return true;
            }
            if (entity instanceof Group && groupIds != null && groupIds.contains(entity.getId())) {
                return true;
            }
        }
        return false;
    }

    private void checkSubTaskStrategy(final Task task) {
        for (SubTasksStrategy strategy : task.getSubTaskStrategies()) {
            strategy.execute(this, service, task);
        }

        final Task parentTask;
        if (task.getTaskData().getParentId() != -1) {
            parentTask = getTask(task.getTaskData().getParentId());
            for (SubTasksStrategy strategy : parentTask.getSubTaskStrategies()) {
                strategy.execute(this, service, parentTask);
            }
        }
    }

    /**
     * Returns the entity of the specified class by for the specified primaryKey.
     *
     * @param entityClass - class of entity to return
     * @param primaryKey  - key of entity
     * @return entity or <code>EntityNotFoundException</code> if the entity cannot be found
     * @throws EntityNotFoundException if entity not found
     */
    private <T> T getEntity(final Class<T> entityClass, final Object primaryKey) {
        final T entity = em.find(entityClass, primaryKey);

        if (entity == null) {
            throw new EntityNotFoundException("No " + entityClass.getSimpleName() + " with ID " + primaryKey + " was found!");
        }

        return entity;
    }
   
    /**
     * Persists the specified object within a new transaction. If there are any problems, the
     * transaction will be rolled back.
     *
     * @param object object to persists
     */
    private void persistInTransaction(final Object object) {
        doOperationInTransaction(new TransactedOperation() {
            public void doOperation() {
                em.persist(object);
            }
        });
    }

    /**
     * Starts a transaction if there isn't one currently in progess.
     */
    private void beginOrUseExistingTransaction() {
        final EntityTransaction tx = em.getTransaction();

        if (!tx.isActive()) {
            tx.begin();
        }
    }

    /**
     * Executes the specified operation within a transaction. Note that if there is a currently active
     * transaction, if will reuse it.
     *
     * @param operation operation to execute
     */
    private void doOperationInTransaction(final TransactedOperation operation) {
        final EntityTransaction tx = em.getTransaction();

        try {
            if (!tx.isActive()) {
                tx.begin();
            }

            operation.doOperation();

            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
        }
    }

    private interface TransactedOperation {
        void doOperation();
    }
}
