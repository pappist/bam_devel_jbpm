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

package org.jbpm.userprofile;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.contexts.Lifecycle;
import org.jbpm.userprofile.DroolsTaskUserProfile;
import org.jbpm.userprofile.User;
import org.jbpm.userprofile.UserProfileManager;

public class UserProfileManagerTest extends TestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testFileBasedUserProfileRepository() throws Exception {
    	//Mock up SEAM contexts
    	Map application = new HashMap<String, Object>();
    	Lifecycle.beginApplication(application);
    	Lifecycle.beginCall();
    	MockIdentity midentity = new MockIdentity();
    	Contexts.getSessionContext().set("org.jboss.seam.security.identity", midentity);


    	UserProfileManager upm = new UserProfileManager();
    	upm.setUserProfileRepository(new MockFileBasedUserProfileRepository());

    	User user = (User)upm.getUser();
    	assertEquals(user.getId(), "mockedUser");

    	DroolsTaskUserProfile userProfile = (DroolsTaskUserProfile)user.getUserProfile();
    	//assertEquals(userProfile.getDisplayName(entity), "mockedUserName");
    }

}
