package org.ccnx.ccn.test.profiles.security.access.group;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import junit.framework.Assert;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.config.UserConfiguration;
import org.ccnx.ccn.io.content.Link;
import org.ccnx.ccn.profiles.security.access.group.ACL;
import org.ccnx.ccn.profiles.security.access.group.GroupAccessControlManager;
import org.ccnx.ccn.profiles.security.access.group.ACL.ACLObject;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.test.profiles.security.TestUserData;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This test relies on org.ccn.ccn.test.profiles.access.TestUserData to generate users
 * for the access control test.
 *
 */

public class GroupAccessControlManagerTestRepo {

	static GroupAccessControlManager acm;
	static ContentName baseNode, childNode, grandchildNode;
	static ContentName userKeyStorePrefix, userNamespace;
	static int userCount = 3;
	static TestUserData td;
	static String[] friendlyNames;
	static ContentName user0, user1, user2;
	static ACL baseACL, childACL;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// create base node, child node and grandchild node
		Random rand = new Random();
		String directoryBase = "/test/AccessControlManagerTestRepo-";
		baseNode = ContentName.fromNative(directoryBase + Integer.toString(rand.nextInt(10000)));
		childNode = ContentName.fromNative(baseNode, "child");
		grandchildNode = ContentName.fromNative(childNode, "grandchild");
				
		// create user identities with TestUserData
		ContentName testPrefix = UserConfiguration.defaultNamespace();
		userKeyStorePrefix = ContentName.fromNative(testPrefix, "_access_");
		userNamespace = ContentName.fromNative(testPrefix, "home");
		td = new TestUserData(userKeyStorePrefix, userCount, true, "password".toCharArray(), CCNHandle.open());
		td.saveUserPK2Repo(userNamespace);
		friendlyNames = td.friendlyNames().toArray(new String[0]);
		Assert.assertEquals(userCount, friendlyNames.length);
		user0 = ContentName.fromNative(userNamespace, friendlyNames[0]);
		user1 = ContentName.fromNative(userNamespace, friendlyNames[1]);
		user2 = ContentName.fromNative(userNamespace, friendlyNames[2]);
	}
	
	/**
	 * Set the ACL for the base node (we make user 0 a manager of the base node).
	 * @throws Exception
	 */
	@Test
	public void testSetBaseACL() throws Exception {		
		ArrayList<Link> ACLcontents = new ArrayList<Link>();
		Link lk = new Link(user0, "rw+", null);
		ACLcontents.add(lk);
		baseACL = new ACL(ACLcontents);
		CCNHandle handle = td.getHandleForUser(friendlyNames[0]);
		acm = new GroupAccessControlManager(baseNode, handle);
		acm.initializeNamespace(baseACL);
	}

	/**
	 * Retrieve the ACL for the base node.
	 * @throws Exception
	 */
	@Test
	public void testGetBaseACL() throws Exception {
		ACLObject aclo = acm.getEffectiveACLObject(baseNode);
		ACL aclRetrieved = aclo.acl();
		Assert.assertTrue(aclRetrieved.equals(baseACL));
	}
	
	/**
	 * Retrieve the ACL for the grandchild node.
	 * This ACL comes from the base node.
	 * @throws Exception
	 */
	@Test
	public void testGetACLFromAncestor() throws Exception {
		ACLObject aclo = acm.getEffectiveACLObject(grandchildNode);
		Assert.assertTrue(aclo.acl().equals(baseACL));
	}
	
	/**
	 * Interpose a different ACL at the child node (we make user0 a manager and user1 a reader)
	 * Retrieve the ACL for the grandchild node and check that it now comes 
	 * from the child node.
	 * @throws Exception
	 */
	@Test
	public void testSetACL() throws Exception {
		// set interposed ACL
		ArrayList<Link> newACLContents = new ArrayList<Link>();
		newACLContents.add(new Link(user0, "rw+", null));
		newACLContents.add(new Link(user1, "r", null));
		childACL = new ACL(newACLContents);
		acm.setACL(childNode, childACL);
		Thread.sleep(1000);
		
		// retrieve ACL at child node
		ACLObject aclo = acm.getEffectiveACLObject(childNode);
		Assert.assertTrue(aclo.acl().equals(childACL));
		
		// retrieve ACL at grandchild node
		aclo = acm.getEffectiveACLObject(grandchildNode);
		Assert.assertTrue(aclo.acl().equals(childACL));
	}
	
	/**
	 * Update the child ACL to add user1 as a writer and user2 as a reader.
	 * @throws Exception
	 */
	@Test
	public void testUpdateACLAdd() throws Exception {
		ArrayList<Link> newReaders = new ArrayList<Link>();
		newReaders.add(new Link(user2));
		acm.addReaders(childNode, newReaders);
		ArrayList<Link> newWriters = new ArrayList<Link>();
		newWriters.add(new Link(user1));
		acm.addWriters(childNode, newWriters);
		
		// get the ACL (at the grandchild node) and check the updates
		ACL aclRetrieved = acm.getEffectiveACL(grandchildNode);
		LinkedList<Link> childACLLinks = aclRetrieved.contents();
		Iterator<Link> iter = childACLLinks.iterator();
		// user0 is a manager
		Link l = (Link) iter.next();
		Assert.assertTrue(l.targetName().equals(user0));
		Assert.assertTrue(l.targetLabel().equals("rw+"));
		// user2 is a reader
		l = (Link) iter.next();
		Assert.assertTrue(l.targetName().equals(user2));
		Assert.assertTrue(l.targetLabel().equals("r"));
		// user1 is a writer
		l = (Link) iter.next();
		Assert.assertTrue(l.targetName().equals(user1));
		Assert.assertTrue(l.targetLabel().equals("rw"));
		// there is no other link in the ACL
		Assert.assertFalse(iter.hasNext());
	}
	
	/**
	 * Remove user1 as a writer and user2 as a reader of the child node
	 * @throws Exception
	 */
	@Test
	public void testUpdateACLRemove() throws Exception {
		// remove user1 as a writer
		ArrayList<Link> removedWriters = new ArrayList<Link>();
		removedWriters.add(new Link(user1));
		Thread.sleep(1000);
		acm.removeWriters(childNode, removedWriters);
		// remove user2 as a reader
		ArrayList<Link> removedReaders = new ArrayList<Link>();
		removedReaders.add(new Link(user2));
		acm.removeReaders(childNode, removedReaders);
		
		// get the ACL (at the child node) and check the updates
		ACL aclRetrieved = acm.getEffectiveACL(grandchildNode);
		LinkedList<Link> childACLLinks = aclRetrieved.contents();
		Iterator<Link> iter = childACLLinks.iterator();
		// user0 is a manager
		Link l = (Link) iter.next();
		Assert.assertTrue(l.targetName().equals(user0));
		Assert.assertTrue(l.targetLabel().equals("rw+"));
		// there is no other link in the ACL
		Assert.assertFalse(iter.hasNext());
	}
	
	/**
	 * Delete the ACL at the child node.
	 * Retrieve the ACL for the grandchild node and check that it comes from the base node.
	 * @throws Exception
	 */
	@Test
	public void deleteACL() throws Exception {
		acm.deleteACL(childNode);
		// retrieve ACL at grandchild node
		ACLObject aclo = acm.getEffectiveACLObject(grandchildNode);
		Assert.assertTrue(aclo.acl().equals(baseACL));
	}
	
}