package net.sf.webdav.locking;

import java.security.Principal;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import net.sf.webdav.ILockingListener;
import net.sf.webdav.ITransaction;

/** @author knappmeier */
public class ResourceLocksTest {

    public static final ITransaction USER = new ITransaction() {
        @Override
        public Principal getPrincipal() {
            return new Principal() {
				
				@Override
				public String getName() {
					return "username";
				}
			};
        }

        @Override
        public String toString() {
            return getPrincipal().getName();
        }
    };

    public static final ITransaction OTHER_USER = new ITransaction() {
        @Override
        public Principal getPrincipal() {
            return new Principal() {
				
				@Override
				public String getName() {
					return "other";
				}
			};
        }

        @Override
        public String toString() {
            return getPrincipal().getName();
        }
    };
    private ResourceLocks resourceLocks;
    private MyILockingListener lockingListener;

    @Before
    public void setup() {
        lockingListener = new MyILockingListener();
        resourceLocks = new ResourceLocks(lockingListener);
    }

    @Test
    public void testLockUnlockWithListener() throws Exception {

        Assert.assertTrue("First lock should succeed", resourceLocks.exclusiveLock(USER, "/path/file.doc", "owner", 0, 300));
        Assert.assertFalse("Lock of other user should fail", resourceLocks.exclusiveLock(OTHER_USER, "/path/file.doc", "owner2", 0, 300));
        LockedObject lockedObjectByPath = resourceLocks.getLockedObjectByPath(USER, "/path/file.doc");
        Assert.assertTrue(resourceLocks.unlock(USER, lockedObjectByPath.getID(), "owner"));

        Assert.assertEquals("Checking number of 'onLock'-calls",2,lockingListener.lockEvents.size());
        // TODO: For some reason, only the requested lock is removed. The parent lock is preserved. Is that right?
        Assert.assertEquals("Checking number of 'onUnlock'-calls",1,lockingListener.unlockEvents.size());
    }

    @Test
    public void testLockTimeoutWithListener() throws Exception {

        Assert.assertTrue(resourceLocks.exclusiveLock(USER, "/path/file.doc", "owner", 0, 1));

        Thread.sleep(3000);

        resourceLocks.checkTimeouts(null,false);
        Assert.assertEquals("Checking number of 'onLock'-calls",2,lockingListener.lockEvents.size());
        Assert.assertEquals("Checking number of 'onUnlock'-calls",2,lockingListener.unlockEvents.size());
    }


    private static class MyILockingListener implements ILockingListener {

        private List<LockUnlockEvent> lockEvents = new LinkedList<LockUnlockEvent>();
        private List<LockUnlockEvent> unlockEvents = new LinkedList<LockUnlockEvent>();


        @Override
        public void onLockResource(ITransaction transaction, String resourceUri) {
            lockEvents.add(new LockUnlockEvent(transaction, resourceUri));
        }

        @Override
        public void onUnlockResource(ITransaction transaction, String resourceUri) {
            unlockEvents.add(new LockUnlockEvent(transaction, resourceUri));
        }

    }


    private static class LockUnlockEvent {
        String resourceUri;
        ITransaction transaction;

        private LockUnlockEvent(ITransaction transaction, String resourceUri) {
            this.resourceUri = resourceUri;
            this.transaction = transaction;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("LockUnlockEvent{");
            sb.append("resourceUri='").append(resourceUri).append('\'');
            sb.append(", transaction=").append(transaction);
            sb.append('}');
            return sb.toString();
        }
    }
}
