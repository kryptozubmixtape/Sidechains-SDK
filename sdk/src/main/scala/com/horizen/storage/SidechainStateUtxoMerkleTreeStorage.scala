package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.cryptolibprovider.InMemorySparseMerkleTreeWrapper
import com.horizen.librustsidechains.FieldElement
import com.horizen.utils.{ByteArrayWrapper, UtxoMerkleTreeLeafInfo, UtxoMerkleTreeLeafInfoSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import java.util.{ArrayList => JArrayList, List => JList}
import com.horizen.utils.{Pair => JPair}

class SidechainStateUtxoMerkleTreeStorage(storage: Storage)
  extends ScorexLogging with SidechainTypes {

  var merkleTreeWrapper: InMemorySparseMerkleTreeWrapper = loadMerkleTree()

  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")

  private def loadMerkleTree(): InMemorySparseMerkleTreeWrapper = {
    // TODO: choose proper merkle tree height compatible with the CSW circuit.
    val treeHeight = 12
    val merkleTree = new InMemorySparseMerkleTreeWrapper(treeHeight)

    val newLeaves: Seq[JPair[FieldElement, Integer]] = getAllLeavesInfo.map(leafInfo => {
      new JPair(FieldElement.deserialize(leafInfo.leaf), Integer.valueOf(leafInfo.position))
    })
    merkleTree.addLeaves(newLeaves.asJava)
    newLeaves.foreach(pair => pair.getKey.freeFieldElement())

    merkleTree
  }

  private def calculateLeaf(box: SidechainTypes#SCB): FieldElement = {
    FieldElement.createRandom()
  }

  def calculateKey(boxId: Array[Byte]): ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(boxId))
  }

  def getLeafInfo(boxId: Array[Byte]) : Option[UtxoMerkleTreeLeafInfo] = {
    storage.get(calculateKey(boxId)) match {
      case v if v.isPresent =>
        UtxoMerkleTreeLeafInfoSerializer.parseBytesTry(v.get().data) match {
          case Success(leafInfo) => Option(leafInfo)
          case Failure(exception) =>
            log.error("Error while UtxoMerkleTreeLeafInfo parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getAllLeavesInfo: Seq[UtxoMerkleTreeLeafInfo] = {
    storage.getAll
      .asScala
      .map(pair => UtxoMerkleTreeLeafInfoSerializer.parseBytes(pair.getValue.data))
  }

  def getMerkleTreeRoot: Array[Byte] = merkleTreeWrapper.calculateRoot()

  def update(version: ByteArrayWrapper,
             boxesToAppend: Seq[SidechainTypes#SCB],
             boxesToRemoveSet: Set[ByteArrayWrapper]): Try[SidechainStateUtxoMerkleTreeStorage] = Try {
    require(boxesToAppend != null, "List of boxes to add must be NOT NULL. Use empty List instead.")
    require(boxesToRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")

    val removeSeq: Set[ByteArrayWrapper] = boxesToRemoveSet.map(id => calculateKey(id.data))

    // Remove leaves from inmemory tree
    require(merkleTreeWrapper.removeLeaves(removeSeq.flatMap(id => {
      getLeafInfo(id.data).map(leafInfo => Integer.valueOf(leafInfo.position))
    }).toList.asJava), "Failed to remove leaves from UtxoMerkleTree")

    // Collect positions for the new leaves and check that there is enough empty space in the tree
    val newLeavesPositions = merkleTreeWrapper.leftmostEmptyPositions(boxesToAppend.size).asScala
    if (newLeavesPositions.size != boxesToAppend.size) {
      throw new IllegalStateException("Not enough empty leaves in the UTXOMerkleTree.")
    }

    val leavesToAppend = boxesToAppend.map(box => (box.id(), calculateLeaf(box))).zip(newLeavesPositions)

    // Add leaves to inmemory tree
    require(merkleTreeWrapper.addLeaves(leavesToAppend.map {
      case ((_, leaf: FieldElement), position: Integer) => new JPair[FieldElement, Integer](leaf, position)
    }.asJava), "Failed to add leaves to UtxoMekrleTree")

    val updateList: JList[JPair[ByteArrayWrapper, ByteArrayWrapper]] = leavesToAppend.map {
      case ((boxId: Array[Byte], leaf: FieldElement), position: Integer) =>
        new JPair[ByteArrayWrapper, ByteArrayWrapper](
          new ByteArrayWrapper(boxId),
          new ByteArrayWrapper(UtxoMerkleTreeLeafInfo(leaf.serializeFieldElement(), position).bytes)
        )
    }.asJava

    storage.update(version, updateList, removeSeq.toList.asJava)

    this
  }.recoverWith{
    case exception =>
      // Reload merkle tree in case of any exception to restore the proper state.
      merkleTreeWrapper = loadMerkleTree()
      Failure(exception)
  }

  def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions : Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainStateUtxoMerkleTreeStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    // Reload merkle tree
    merkleTreeWrapper = loadMerkleTree()
    this
  }

  def isEmpty: Boolean = storage.isEmpty
}