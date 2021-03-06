package protocol.engines;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import model.Entity;
import model.codec.EntityType;
import model.crypto.Signature;
import model.exceptions.LightChainNetworkingException;
import model.lightchain.*;
import model.local.Local;
import network.Channels;
import network.Conduit;
import network.Network;
import network.p2p.P2pNetwork;
import protocol.Engine;
import protocol.NewBlockSubscriber;
import protocol.Parameters;
import protocol.Tags;
import protocol.assigner.LightChainValidatorAssigner;
import state.State;
import storage.Blocks;
import storage.Transactions;

/**
 * Proposer engine encapsulates the logic of creating new blocks.
 */
public class ProposerEngine implements NewBlockSubscriber, Engine {
  private final ReentrantLock lock = new ReentrantLock();
  private final Local local;
  private final Blocks blocks;
  private final Transactions pendingTransactions;
  private final State state;
  private final Conduit proposerCon;
  private final Conduit validatedCon;
  private final Network net;
  private final LightChainValidatorAssigner assigner;
  private final ArrayList<BlockApproval> approvals;
  public Block newB;

  /**
   * Constructor.
   *
   * @param blocks              Blocks storage.
   * @param pendingTransactions Pending transactions storage.
   * @param state               State storage.
   * @param local               Local storage.
   * @param net                 Network.
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "fields are intentionally mutable externally")
  public ProposerEngine(Blocks blocks, Transactions pendingTransactions, State state,
                        Local local, Network net, LightChainValidatorAssigner assigner) {
    this.local = local;
    this.blocks = blocks;
    this.pendingTransactions = pendingTransactions;
    this.state = state;
    this.approvals = new ArrayList<>();
    proposerCon = net.register(this, Channels.ProposedBlocks);
    validatedCon = net.register(this, Channels.ValidatedBlocks);
    this.net = net;
    this.assigner = assigner;
  }

  /**
   * OnNewFinalizedBlock notifies the proposer engine of a new validated block. The proposer engine runs validator
   * assigner with the proposer tag and number of validators of 1. If this node is selected, it means that the
   * proposer engine must create a new block.
   * ---
   * Creating new block: proposer engine has a shared storage component with ingest engine, i.e., transactions and
   * blocks. If the minimum number of validated transactions in the pending transactions' storage are available, then
   * proposer engine fetches them, creates a block out of them, runs validator assignment with validation tag, and
   * sends it to the validators. If it does not have minimum number of validated transactions, it waits till it
   * the minimum number is satisfied.
   *
   * @param blockHeight block height.
   * @param blockId     identifier of block.
   * @throws IllegalStateException    when it receives a new validated block while it is pending for its previously
   *                                  proposed block to get validated.
   * @throws IllegalArgumentException when its parameters do not match a validated block from database.
   */
  @Override
  public void onNewValidatedBlock(int blockHeight, Identifier blockId) throws IllegalStateException,
          IllegalArgumentException {
    if (!blocks.has(blockId) && !blocks.atHeight(blockHeight).id().equals(blockId)) {
      throw new IllegalArgumentException("block is not in database");
    }
    if (lock.isLocked()) {
      throw new IllegalStateException("proposer engine is already running.");
    }
    lock.lock();
    try {
      // Adds the Block Proposer tag to the assigner.
      byte[] bytesId = blockId.getBytes();
      byte[] bytesTag = Tags.BlockProposerTag.getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try {
        output.write(bytesId, 0, 32);
        output.write(bytesTag);
      } catch (IOException e) {
        throw new IllegalStateException("could not write to bytes to ByteArrayOutputStream", e);
      }
      Identifier taggedId = new Identifier(output.toByteArray());

      Assignment assignment = assigner.assign(taggedId, state.atBlockId(blockId), (short) 1);
      // Checks whether the assigner has assigned this node.
      if (assignment.has(local.myId())) {

        // Waits until there are enough pending transactions.
        while (pendingTransactions.size() < Parameters.MIN_VALIDATED_TRANSACTIONS_NUM) {
        }

        ValidatedTransaction[] transactions = new ValidatedTransaction[Parameters.MIN_VALIDATED_TRANSACTIONS_NUM];
        for (int i = 0; i < Parameters.MIN_VALIDATED_TRANSACTIONS_NUM; i++) {
          Transaction tx = pendingTransactions.all().get(i);
          transactions[i] = ((ValidatedTransaction) tx);
          pendingTransactions.remove(tx.id());
        }

        Block newBlock = new Block(blockId, local.myId(), blockHeight + 1, transactions);
        newB = newBlock;
        Signature sign = local.signEntity(newBlock);
        newBlock.setSignature(sign);

        // Adds the Block Proposer tag to the assigner.
        bytesId = newBlock.id().getBytes();
        bytesTag = Tags.ValidatorTag.getBytes(StandardCharsets.UTF_8);
        output = new ByteArrayOutputStream();
        try {
          output.write(bytesId, 0, 32);
          output.write(bytesTag);
        } catch (IOException e) {
          throw new IllegalStateException("could not write to bytes to ByteArrayOutputStream", e);
        }
        taggedId = new Identifier(output.toByteArray());
        assignment = assigner.assign(taggedId, state.atBlockId(newBlock.getPreviousBlockId()),
                Parameters.VALIDATOR_THRESHOLD);
        for (Identifier id : assignment.all()) {
          try {
            proposerCon.unicast(newBlock, id);
          } catch (LightChainNetworkingException e) {
            e.printStackTrace();
          }
        }
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * The received entity must be only of the BlockApproval type.
   * When a BlockApproval arrives, proposer engine checks if the approval belongs for its recently proposed block, and
   * if it is the case, the approval is stored. When the proposer engine obtains enough approval on its recently
   * proposed block, it creates a validated block out of them and sends it to all nodes (including itself) over the
   * network using the validated blocks channel.
   *
   * @param e the arrived Entity from the network.
   * @throws IllegalArgumentException any entity other than BlockApproval.
   */
  @Override
  public void process(Entity e) throws IllegalArgumentException {
    if (e.type() == EntityType.TYPE_BLOCK_APPROVAL || ((BlockApproval) e).getBlockId() == null) {
      approvals.add((BlockApproval) e);
      if (approvals.size() >= Parameters.VALIDATOR_THRESHOLD) {
        Signature[] signs = new Signature[Parameters.VALIDATOR_THRESHOLD];
        for (int i = 0; i < approvals.size(); i++) {
          signs[i] = approvals.get(i).getSignature();
        }
        ValidatedBlock validatedBlock = new ValidatedBlock(newB.getPreviousBlockId(),
                newB.getProposer(),
                newB.getTransactions(),
                local.signEntity(newB),
                signs,
                newB.getHeight());
        for (Map.Entry<Identifier, String> pair : ((P2pNetwork) net).getIdToAddressMap().entrySet()) {
          if (pair.getValue().equals(Channels.ValidatedBlocks)) {
            try {
              validatedCon.unicast(validatedBlock, pair.getKey());
            } catch (LightChainNetworkingException e1) {
              e1.printStackTrace();
            }
          }
        }
        approvals.clear();
        newB = null;
      }

    } else {
      throw new IllegalArgumentException("entity is not of type BlockApproval");
    }
  }
}