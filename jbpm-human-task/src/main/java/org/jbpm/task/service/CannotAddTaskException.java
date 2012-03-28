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

/**
 * Exception thrown from the <code>TaskServiceSession</code> when there is a problem adding task. This exception
 * is specififically thrown when custom logic in rules has not allowed the <code>Task</code> to be added.
 *
 * @author <a href="mailto:stampy88@yahoo.com">dave sinclair</a>
 * @see org.jbpm.task.service.TaskServiceSession#addTask(org.jbpm.task.Task, ContentData)
 */
public class CannotAddTaskException extends TaskException {
    public CannotAddTaskException(String message) {
        super(message);
    }

    public CannotAddTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
