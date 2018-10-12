package net.consensys.pantheon.ethereum.eth.transactions;

import static java.util.Collections.singletonList;
import static net.consensys.pantheon.ethereum.core.InMemoryWorldState.createInMemoryWorldStateArchive;
import static org.assertj.core.util.Preconditions.checkNotNull;

import net.consensys.pantheon.crypto.SECP256K1;
import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.BlockHashFunction;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.TransactionPool;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.db.WorldStateArchive;
import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.eth.manager.EthContext;
import net.consensys.pantheon.ethereum.eth.manager.EthProtocolManager;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.NetworkRunner;
import net.consensys.pantheon.ethereum.p2p.api.P2PNetwork;
import net.consensys.pantheon.ethereum.p2p.api.PeerConnection;
import net.consensys.pantheon.ethereum.p2p.config.DiscoveryConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import net.consensys.pantheon.ethereum.p2p.config.RlpxConfiguration;
import net.consensys.pantheon.ethereum.p2p.netty.NettyP2PNetwork;
import net.consensys.pantheon.ethereum.p2p.peers.DefaultPeer;
import net.consensys.pantheon.ethereum.p2p.peers.Endpoint;
import net.consensys.pantheon.ethereum.p2p.peers.Peer;
import net.consensys.pantheon.ethereum.p2p.peers.PeerBlacklist;
import net.consensys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestNode implements Closeable {

  private static final Logger LOG = LogManager.getLogger();

  protected final Integer port;
  protected final SECP256K1.KeyPair kp;
  protected final P2PNetwork network;
  protected final Peer selfPeer;
  protected final Map<PeerConnection, DisconnectReason> disconnections = new HashMap<>();
  private final TransactionPool transactionPool;

  public TestNode(
      final Vertx vertx,
      final Integer port,
      final SECP256K1.KeyPair kp,
      final DiscoveryConfiguration discoveryCfg) {
    checkNotNull(vertx);
    checkNotNull(discoveryCfg);

    final int listenPort = port != null ? port : 0;
    this.kp = kp != null ? kp : SECP256K1.KeyPair.generate();

    final NetworkingConfiguration networkingConfiguration =
        NetworkingConfiguration.create()
            .setDiscovery(discoveryCfg)
            .setRlpx(RlpxConfiguration.create().setBindPort(listenPort))
            .setSupportedProtocols(EthProtocol.get());

    final GenesisConfig<Void> genesisConfig = GenesisConfig.development();
    final ProtocolSchedule<Void> protocolSchedule = genesisConfig.getProtocolSchedule();
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);
    final KeyValueStorage kv = new InMemoryKeyValueStorage();
    final MutableBlockchain blockchain =
        new DefaultMutableBlockchain(genesisConfig.getBlock(), kv, blockHashFunction);
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    genesisConfig.writeStateTo(worldStateArchive.getMutable());
    final ProtocolContext<Void> protocolContext =
        new ProtocolContext<>(blockchain, worldStateArchive, null);
    final EthProtocolManager ethProtocolManager = new EthProtocolManager(blockchain, 1, false, 1);

    final NetworkRunner networkRunner =
        NetworkRunner.builder()
            .subProtocols(EthProtocol.get())
            .protocolManagers(singletonList(ethProtocolManager))
            .network(
                capabilities ->
                    new NettyP2PNetwork(
                        vertx,
                        this.kp,
                        networkingConfiguration,
                        capabilities,
                        ethProtocolManager,
                        new PeerBlacklist()))
            .build();
    network = networkRunner.getNetwork();
    this.port = network.getSelf().getPort();
    network.subscribeDisconnect(
        (connection, reason, initiatedByPeer) -> disconnections.put(connection, reason));

    final EthContext ethContext = ethProtocolManager.ethContext();
    transactionPool =
        TransactionPoolFactory.createTransactionPool(protocolSchedule, protocolContext, ethContext);
    networkRunner.start();

    selfPeer = new DefaultPeer(id(), endpoint());
  }

  public BytesValue id() {
    return kp.getPublicKey().getEncodedBytes();
  }

  public static String shortId(final BytesValue id) {
    return id.slice(62).toString().substring(2);
  }

  public String shortId() {
    return shortId(id());
  }

  public Endpoint endpoint() {
    checkNotNull(
        port, "Must either pass port to ctor, or call createNetwork() first to set the port");
    return new Endpoint(
        InetAddress.getLoopbackAddress().getHostAddress(), port, OptionalInt.of(port));
  }

  public Peer selfPeer() {
    return selfPeer;
  }

  public CompletableFuture<PeerConnection> connect(final TestNode remoteNode) {
    return network.connect(remoteNode.selfPeer());
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void close() throws IOException {
    IOException firstEx = null;
    try {
      network.close();
    } catch (final IOException e) {
      if (firstEx == null) {
        firstEx = e;
      }
      LOG.warn("Error closing.  Continuing", e);
    }

    if (firstEx != null) {
      throw new IOException("Unable to close successfully.  Wrapping first exception.", firstEx);
    }
  }

  @Override
  public String toString() {
    return shortId()
        + "@"
        + selfPeer.getEndpoint().getHost()
        + ':'
        + selfPeer.getEndpoint().getTcpPort();
  }

  public void receiveRemoteTransaction(final Transaction transaction) {
    transactionPool.addRemoteTransactions(singletonList(transaction));
  }

  public void receiveLocalTransaction(final Transaction transaction) {
    transactionPool.addLocalTransaction(transaction);
  }

  public int getPendingTransactionCount() {
    return transactionPool.getPendingTransactions().size();
  }
}