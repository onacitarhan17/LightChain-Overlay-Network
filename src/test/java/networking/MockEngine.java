package networking;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import model.Entity;
import protocol.Engine;

/**
 * Represents a mock implementation of Engine interface for testing.
 */
public class MockEngine implements Engine {
  private final ReentrantReadWriteLock lock;
  private final Set<String> receivedEntityIds;

  public MockEngine() {
    this.receivedEntityIds = new HashSet<>();
    this.lock = new ReentrantReadWriteLock();
  }

  /**
   * Called by Network whenever an Entity is arrived for this engine.
   *
   * @param e the arrived Entity from the network.
   * @throws IllegalArgumentException any unhappy path taken on processing the Entity.
   */

  @Override
  public void process(Entity e) throws IllegalArgumentException {
    lock.writeLock();
    receivedEntityIds.add(e.id().toString());
    lock.writeLock();
  }

  /**
   * Check whether an entity is received.
   *
   * @param e the entitiy.
   * @return true if the entity received, otherwise false.
   */
  public boolean hasReceived(Entity e) {
    lock.readLock();
    boolean ok = this.receivedEntityIds.contains(e.id().toString());
    lock.readLock();
    return ok;
  }

}