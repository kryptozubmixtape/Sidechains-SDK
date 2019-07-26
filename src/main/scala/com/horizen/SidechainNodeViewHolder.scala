package com.horizen

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import akka.actor.{ActorRef, ActorSystem, Props}
import com.horizen.block.SidechainBlock
import com.horizen.box.BoxSerializer
import com.horizen.companion.{SidechainBoxesCompanion, SidechainSecretsCompanion, SidechainTransactionsCompanion}
import com.horizen.secret.SecretSerializer
import com.horizen.state.{ApplicationState, DefaultApplicationState}
import com.horizen.wallet.{ApplicationWallet, DefaultApplicationWallet}
import scorex.core.settings.ScorexSettings
import scorex.core.utils.NetworkTimeProvider
import scorex.util.ScorexLogging
import scorex.core.{VersionTag, bytesToVersion, idToBytes, idToVersion, versionToBytes}


import scala.util.{Failure, Success, Try}

class SidechainNodeViewHolder(sidechainSettings: SidechainSettings,
                              timeProvider: NetworkTimeProvider,
                              sidechainBoxesCompanion: SidechainBoxesCompanion,
                              sidechainSecretsCompanion: SidechainSecretsCompanion,
                              applicationWallet: ApplicationWallet,
                              applicationState: ApplicationState)
  extends scorex.core.NodeViewHolder[SidechainTypes#SCBT, SidechainBlock]
  with ScorexLogging
  with SidechainTypes
{
  override type SI = SidechainSyncInfo
  override type HIS = SidechainHistory
  override type MS = SidechainState
  override type VL = SidechainWallet
  override type MP = SidechainMemoryPool

  override val scorexSettings: ScorexSettings = sidechainSettings.scorexSettings;

  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    val history = Some(new SidechainHistory())
    val wallet = SidechainWallet.restoreWallet(sidechainSettings, applicationWallet, sidechainBoxesCompanion, sidechainSecretsCompanion, None)
    val state = SidechainState.restoreState(sidechainSettings, applicationState, sidechainBoxesCompanion, None)
    val pool = SidechainMemoryPool.emptyPool

    if (history.isDefined && wallet.isDefined && state.isDefined)
      Some((history.get, state.get, wallet.get, pool))
    else
      None
  }

  override protected def genesisState: (HIS, MS, VL, MP) = {
    try {
      val history = Some(new SidechainHistory())
      val state = SidechainState.genesisState(sidechainSettings, applicationState, sidechainBoxesCompanion, None)
      val wallet = SidechainWallet.genesisWallet(sidechainSettings, applicationWallet, sidechainBoxesCompanion, sidechainSecretsCompanion, None)
      val pool = SidechainMemoryPool.emptyPool

      if (history.isDefined && wallet.isDefined && state.isDefined)
        (history.get, state.get, wallet.get, pool)
      else {
        if (history.isEmpty)
          throw new RuntimeException("History storage is not empty.")

        if (state.isEmpty)
          throw new RuntimeException("State storage is not empty.")

        if (wallet.isEmpty)
          throw new RuntimeException("WalletBox storage is not empty.")

        (null, null, null, null)
      }

    } catch {
      case exception : Throwable =>
        log.error ("Error during creation genesis state.", exception)
        throw exception
    }
  }

  // TO DO: Put it into NodeViewSynchronizerRef::modifierSerializers. Also put here map of custom sidechain transactions
  /*val customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] = ???

  override val modifierSerializers: Map[Byte, Serializer[_ <: NodeViewModifier]] =
    Map(new RegularTransaction().modifierTypeId() -> new SidechainTransactionsCompanion(customTransactionSerializers))
  */
}

object SidechainNodeViewHolderRef {
  def props(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState): Props =
    Props(new SidechainNodeViewHolder(settings, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      applicationWallet, applicationState))

  def apply(settings: SidechainSettings,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      applicationWallet, applicationState))

  def apply(name: String,
            settings: SidechainSettings,
            timeProvider: NetworkTimeProvider,
            sidechainBoxesCompanion: SidechainBoxesCompanion,
            sidechainSecretsCompanion: SidechainSecretsCompanion,
            applicationWallet: ApplicationWallet,
            applicationState: ApplicationState)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider, sidechainBoxesCompanion, sidechainSecretsCompanion,
      applicationWallet, applicationState), name)
}