package com.uchain.core;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.iq80.leveldb.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uchain.common.Serializabler;
import com.uchain.core.consensus.ForkBase;
import com.uchain.core.consensus.ForkItem;
import com.uchain.core.consensus.TwoTuple;
import com.uchain.core.datastore.AccountStore;
import com.uchain.core.datastore.BlkTxMappingStore;
import com.uchain.core.datastore.DataStoreConstant;
import com.uchain.core.datastore.HeadBlockStore;
import com.uchain.core.datastore.HeaderStore;
import com.uchain.core.datastore.HeightStore;
import com.uchain.core.datastore.NameToAccountStore;
import com.uchain.core.datastore.ProducerStateStore;
import com.uchain.core.datastore.TransactionStore;
import com.uchain.core.datastore.keyvalue.AccountValue;
import com.uchain.core.datastore.keyvalue.BlkTxMappingValue;
import com.uchain.core.datastore.keyvalue.BlockHeaderValue;
import com.uchain.core.datastore.keyvalue.HeadBlock;
import com.uchain.core.datastore.keyvalue.HeadBlockValue;
import com.uchain.core.datastore.keyvalue.IntKey;
import com.uchain.core.datastore.keyvalue.ProducerStatus;
import com.uchain.core.datastore.keyvalue.ProducerStatusValue;
import com.uchain.core.datastore.keyvalue.StringKey;
import com.uchain.core.datastore.keyvalue.TransactionValue;
import com.uchain.core.datastore.keyvalue.UInt160Key;
import com.uchain.core.datastore.keyvalue.UInt256Key;
import com.uchain.core.datastore.keyvalue.UInt256Value;
import com.uchain.crypto.BinaryData;
import com.uchain.crypto.CryptoUtil;
import com.uchain.crypto.Fixed8;
import com.uchain.crypto.MerkleTree;
import com.uchain.crypto.PrivateKey;
import com.uchain.crypto.PublicKey;
import com.uchain.crypto.Scalar;
import com.uchain.crypto.UInt160;
import com.uchain.crypto.UInt256;
import com.uchain.main.Settings;
import com.uchain.storage.ConnFacory;
import com.uchain.storage.LevelDbStorage;

import lombok.Getter;
import lombok.Setter;
import lombok.val;

@Getter
@Setter
public class LevelDBBlockChain implements BlockChain{
	private static final Logger log = LoggerFactory.getLogger(ForkBase.class);
    private LevelDbStorage db;
    private Settings settings;
    ForkBase forkBase = new ForkBase(settings);
    
    LevelDBBlockChain(Settings settings){
    	this.db = ConnFacory.getInstance(settings.getChainSettings().getChain_dbDir());
    	this.settings = settings;
        HeadBlock headBlockStore = headBlkStore.get();
        if(headBlockStore == null) reInit();
        else init(headBlockStore);
        
        if (forkBase.head() == null) {
        	TwoTuple<List<ForkItem>,Boolean> twoTuple = forkBase.add(genesisBlock);
        	List<ForkItem> saveBlocks = twoTuple.first;
        	WriteBatch batch = db.getBatchWrite();
        	saveBlocks.forEach(item -> {
        		onConfirmed(item.getBlock());
				batch.delete(Serializabler.toBytes(item.getBlock().id()));
    		});
    		db.BatchWrite(batch);
        }
    }

    private BinaryData genesisProducer = new BinaryData(settings.getChainSettings().getChain_genesis_publicKey()); // TODO: read from settings
    private PrivateKey genesisProducerPrivKey = new PrivateKey(Scalar.apply(new BinaryData(settings.getChainSettings().getChain_genesis_privateKey())));

    private HeaderStore headerStore = new HeaderStore(db, 10, DataStoreConstant.HeaderPrefix,
            new UInt256Key(), new BlockHeaderValue());
    private HeightStore heightStore = new HeightStore(db, 10, DataStoreConstant.HeightToIdIndexPrefix,
            new IntKey(), new UInt256Value());
    private TransactionStore txStore = new TransactionStore(db, 10, DataStoreConstant.TxPrefix,
            new UInt256Key(), new TransactionValue());
    private AccountStore accountStore = new AccountStore(db, 10, DataStoreConstant.AccountPrefix,
            new UInt160Key(), new AccountValue());
    //  private val addressStore = new AddressStore(db)
    private BlkTxMappingStore blkTxMappingStore = new BlkTxMappingStore(db, 10,
            DataStoreConstant.BlockIdToTxIdIndexPrefix, new UInt256Key(), new BlkTxMappingValue());

    private HeadBlockStore headBlkStore = new HeadBlockStore(db, DataStoreConstant.HeadBlockStatePrefix,
            new HeadBlockValue());
    //private val utxoStore = new UTXOStore(db, 10)
    private NameToAccountStore nameToAccountStore = new NameToAccountStore(db, 10,
            DataStoreConstant.NameToAccountIndexPrefix,new StringKey(),new UInt160Key());
    // TODO:  pubkeyNonceStore
    private ProducerStateStore prodStateStore = new ProducerStateStore(db,  DataStoreConstant.ProducerStatePrefix,
            new ProducerStatusValue());


    // TODO: folkBase is todo
    // TODO: zero is not a valid pub key, need to work out other method
    private BinaryData minerCoinFrom = new BinaryData(settings.getChainSettings().getChain_miner());   // 33 bytes pub key
    private Fixed8 minerAward = Fixed8.Ten;

    private UInt160 genesisMinerAddress = UInt160.parse("f54a5851e9372b87810a8e60cdd2e7cfd80b6e31");
    private Transaction genesisTx = new Transaction(TransactionType.Miner, minerCoinFrom,
            genesisMinerAddress, "", minerAward, UInt256.Zero(), 0L,
            CryptoUtil.array2binaryData(BinaryData.empty),CryptoUtil.array2binaryData(BinaryData.empty));

    private BlockHeader genesisBlockHeader =  BlockHeader.build(0, settings.getChainSettings().getChain_genesis_timeStamp(),
            UInt256.Zero(), UInt256.Zero(), genesisProducer, genesisProducerPrivKey);

    private Block genesisBlock = new Block(genesisBlockHeader,Transaction.transactionToArrayList(genesisTx));

    private BlockHeader latestHeader= genesisBlockHeader;

    private ProducerStatus latestProdState = null;

    @Override
    public String getGenesisBlockChainId(){
        return genesisBlock.id() + "";
    }

    @Override
    public BlockChainIterator iterator () {
        return new BlockChainIterator();
    } 
    
    @Override
    public int getHeight(){
        return latestHeader.getIndex();
    }

    @Override
    public long getHeadTime(){
    	return forkBase.head().getBlock().timeStamp();
    }
        
    @Override
    public BlockHeader getLatestHeader(){
    	ForkItem forkHead = forkBase.head();
    	if(forkHead != null) {
    		return forkHead.getBlock().getHeader();
    	}else {
    		return latestHeader;
    	}
    }
    
    @Override
    public long headTimeSinceGenesis(){
        return latestHeader.getTimeStamp() - genesisBlockHeader.getTimeStamp();
    }

    @Override
    public long getDistance(){
        val state = prodStateStore.get();
        assert (state != null);
        return state.getDistance();
    }
    
    @Override
    public BlockHeader getHeader(UInt256 id){
        return headerStore.get(id);
    }

    @Override
    public BlockHeader getHeader(int index){
        val id = heightStore.get(index);
        if(id != null){
            return getHeader(id);
        }
        return null;
    }
    
    @Override
    public UInt256 getNextBlockId(UInt256 id) {
    	UInt256 target = null;
    	Block block = getBlock(id);
    	if(block != null) {
    		Block nextBlock = getBlock(block.height() + 1);
    		if(nextBlock != null) {
    			target = nextBlock.id();
    		}
    	}
    	if(target == null) {
    		target = forkBase.getNext(id);
    	}
    	return target;
    }
    
    
    @Override
    public Block getBlock(UInt256 id){
        val headerBlock = headerStore.get(id);
        if(headerBlock != null){
            val blkTxMapping = blkTxMappingStore.get(headerBlock.id());
            val transactions = new ArrayList<Transaction>(blkTxMapping.getTxIds().size());
            if(blkTxMapping != null){
                blkTxMapping.getTxIds().forEach(key -> {
                    Transaction transaction = txStore.get(key);
                    if(transaction !=  null) transactions.add(transaction);
                });
            }
            return new Block(headerBlock, transactions);
        }
        return null;
    }
    
    @Override
    public Block getBlock(int index){
        val id = heightStore.get(index);
        if(id != null){
            return getBlock(id);
        }
        return null;
    }

    @Override
    public Block getBlockInForkBase(UInt256 id) {
    	ForkItem forkItem = forkBase.get(id);
    	if(forkItem == null) {
    		return null;
    	}else {
    		return forkItem.getBlock();
    	}
    }
    
    @Override
    public boolean containsBlock(UInt256 id){
        return headerStore.contains(id);
    }
    
    /**
     * 产生区块
     */
    @Override
    public Block produceBlock(PublicKey producer, PrivateKey privateKey, long timeStamp,
                              List<Transaction> transactions){
        val minerTx = new Transaction(TransactionType.Miner, minerCoinFrom,
                producer.pubKeyHash(), "", minerAward, UInt256.Zero(), new Long((long)(latestHeader.getIndex() + 1)),
                CryptoUtil.array2binaryData(BinaryData.empty), CryptoUtil.array2binaryData(BinaryData.empty));
        val txs = getUpdateTransaction(minerTx, transactions);
        val merkleRoot = MerkleTree.root(transactions.stream().map(v -> v.id()).collect(Collectors.toList()));
        val header = BlockHeader.build(latestHeader.getIndex() + 1, timeStamp, merkleRoot,
                latestHeader.id(), producer.toBin(), privateKey);
        val block = new Block(header, txs);
        TwoTuple<List<ForkItem>,Boolean> twoTuple = forkBase.add(genesisBlock);
		if (twoTuple.second) {
			return block;
		} else {
			return null;
		}
    }
    
    /**
     * 校验交易，并把矿工交易置为第一条记录
     * @param minerTx
     * @param transactions
     * @return
     */
    private List<Transaction> getUpdateTransaction(Transaction minerTx, List<Transaction> transactions){
        List<Transaction> txs = new ArrayList<Transaction>(transactions.size() + 1);
        transactions.forEach(transaction -> {
            if(verifyTransaction(transaction)) txs.add(transaction);
        });
        txs.add(0,minerTx);
        return txs;
    }
    
    @Override
    public boolean tryInsertBlock(Block block) {
        if (verifyBlock(block))
            if (saveBlockToStores(block))
                return true;
        return false;
    }

    @Override
    public Transaction getTransaction(UInt256 id){
        return  txStore.get(id);
    }

    @Override
    public boolean containsTransaction(UInt256 id) {
        return txStore.contains(id);
    }
    
    /**
	 * 已确认的Block存入数据库
	 * @param block
	 */
	private void onConfirmed(Block block) {
		log.info("confirm block height:"+ block.height()+" block id:"+block.id());
		if(block.height() != 0) {
			saveBlockToStores(block);
		}
	}
	
    private boolean saveBlockToStores(Block block){
        try {
        	WriteBatch batch = db.getBatchWrite();
            headerStore.set(block.getHeader().id(), block.getHeader(), batch);
            heightStore.set(block.getHeader().getIndex(), block.getHeader().id(), batch);
            headBlkStore.set(new HeadBlock(block.getHeader().getIndex(), block.getHeader().id()), batch);
            val transations = new ArrayList<UInt256>(block.getTransactions().size());
            block.getTransactions().forEach(transaction -> {transations.add(transaction.id());});
            val blkTxMapping = new BlkTxMapping(block.id(), transations);
            blkTxMappingStore.set(block.id(), blkTxMapping, batch);
            Map<UInt160, Account> accounts = new HashMap<UInt160, Account>();
            Map<UInt160, Map<UInt256, Fixed8>> balances = new HashMap<>();
            block.getTransactions().forEach(tx -> {
                txStore.set(tx.id(), tx, batch);
                calcBalancesInBlock(balances, true, tx.fromPubKeyHash(), tx.getAmount(), tx.getAssetId());
                calcBalancesInBlock(balances, false, tx.getToPubKeyHash(), tx.getAmount(), tx.getAssetId());
                updateAccout(accounts, tx);
            });
            balances.forEach((accountAddress, balancesInLocalBlk) -> {
                if(accountStore.contains(accountAddress)){
                    val account = accountStore.get(accountAddress);
                    val updateBalances = updateBalancesInAccount(account.getBalances(), balancesInLocalBlk);
                    val updateAccount = new Account(account.isActive(), account.getName(),updateBalances,
                            account.getNextNonce(), account.getVersion(), account.get_id());
                    accountStore.set(accountAddress, updateAccount, batch);
                }
                else{
                    val newAccount = new Account(true, "", getBalancesWithOutAccount(balancesInLocalBlk), 0L);
                    accountStore.set(accountAddress, newAccount, batch);
                }
            });
            latestHeader = block.getHeader();
            db.BatchWrite(batch);
            return true;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
    }
    
	private Map<UInt160, Map<UInt256, Fixed8>> calcBalancesInBlock(
			Map<UInt160, Map<UInt256, Fixed8>> balances, Boolean spent, UInt160 address, Fixed8 amounts,
			UInt256 assetId) {
		Fixed8 amount = amounts;
		if (spent)
			amount = new Fixed8(0L - amounts.getValue());
		if (balances.containsKey(address)) {
			Map<UInt256, Fixed8> balance = balances.get(address);
			Fixed8 amountBeforeTrans = balance.get(assetId);
			Fixed8 amountAfterTrans = new Fixed8(amountBeforeTrans.getValue() + amount.getValue());
			if (balance.containsKey(assetId))
				balance.replace(assetId, amountAfterTrans);
			else
				balance.put(assetId, amountAfterTrans);
		} else {
			Map<UInt256, Fixed8> transRecord = new HashMap<UInt256, Fixed8>();
			transRecord.put(assetId, amount);
			balances.put(address, transRecord);
		}
		return balances;
	}
	
	private Map<UInt256, Fixed8> updateBalancesInAccount(Map<UInt256, Fixed8> balancesInAccount,
			Map<UInt256, Fixed8> balancesInLocalBlk) {
		Map<UInt256, Fixed8> updateBalances = new HashMap<>();
		balancesInAccount.forEach((assetId, balance) -> {
			if (balancesInLocalBlk.containsKey(assetId)) {
				updateBalances.put(assetId, balance.add(balancesInLocalBlk.get(assetId)));
			} else
				updateBalances.put(assetId, balance);
		});
		balancesInLocalBlk.forEach((localAssetId, localBalance) -> {
			if (!balancesInAccount.containsKey(localAssetId)) {
				updateBalances.put(localAssetId, localBalance);
			}
		});
		return updateBalances;
	}

	private Map<UInt256, Fixed8> getBalancesWithOutAccount(Map<UInt256, Fixed8> balancesInLocalBlk){
        HashMap<UInt256, Fixed8> balancesWithOutAccount = new HashMap<>();
        balancesInLocalBlk.forEach((assetId, balance) -> {
            if(balance.greater(Fixed8.Zero)) balancesWithOutAccount.put(assetId, balance);
        });
        return balancesWithOutAccount;
    }
    
	private void updateAccout(Map<UInt160, Account> accounts, Transaction tx){
        // TODO
        return;
    }
	
    @Override
    public boolean verifyBlock(Block block) {
        if (!verifyHeader(block.getHeader()))
            return false;
        if (!forAllTransactionsVerify(block.getTransactions()))
            return false;
        if (!verifyRegisterNames(block.getTransactions()))
            return false;
        return true;
    }

    private boolean verifyHeader(BlockHeader header){
        if (header.getIndex() != latestHeader.getIndex() + 1)
            return false;
        if (header.getTimeStamp() < latestHeader.getTimeStamp())
            return false;
        // TODO: verify rule of timeStamp and producer
        if (header.id().equals(latestHeader.id()))
            return false;
        if (!header.getPrevBlock().equals(latestHeader.id()))
            return false;
        if (header.getProducer().getLength() != 33)
            return false;
        if (!header.verifySig())
            return false;
        return true;
    }
    
    
    private boolean forAllTransactionsVerify(List<Transaction> transactions) {
        boolean flag = true;
        for(Transaction transaction : transactions){
            if(! verifyTransaction(transaction)) {
                flag = false;
                return flag;
            }
        }
        return flag;
    }
	
    private boolean verifyRegisterNames(List<Transaction> transactions){
        val newNames = new ArrayList<String>();
        val registers = new ArrayList<UInt160>();
        for(Transaction tx: transactions){
            if (tx.getTxType() == TransactionType.RegisterName) {
                String name = "";
                try {
                    name = new String(CryptoUtil.binaryData2array(tx.getData()), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (newNames.contains(name)) return false;
                if (registers.contains(tx.fromPubKeyHash())) return false;
                newNames.add(name);
                registers.add(tx.fromPubKeyHash());
            }}
        // make sure name is not used
        return forAllNewNamesVerify(newNames) && !forAllRegistersVerify(registers);
        // make sure register never registed before
    }
    
    private boolean forAllNewNamesVerify(ArrayList<String> names) {
        boolean flag = true;
        for(String name : names){
            if(nameToAccountStore.get(name) == null) {
                flag = false;
                return flag;
            }
        }
        return flag;
    }
    
    private boolean forAllRegistersVerify(ArrayList<UInt160> registers) {
        boolean flag = true;
        for(UInt160 register : registers){
            val account = accountStore.get(register);
            if (account != null && account.getName() != "") {
                flag = false;
                return flag;
            }
        }
        return flag;
    }

    
    @Override
    public boolean verifyTransaction(Transaction tx) {
        if (tx.getTxType() == TransactionType.Miner) {
            // TODO check miner and only one miner tx
            return true;
        }
        val isInvalid = tx.verifySignature();
        return isInvalid && checkAmount();
    }

    boolean checkAmount(){
        return true;
    }
    
    @Override
    public Map<UInt256, Long> getBalance(UInt160 address){
        val account = accountStore.get(address);
        if(account == null) return null;
        else{
            if(account.isActive()){
                Map<UInt256, Long> map = new HashMap<UInt256, Long>();
                val balanceKeys = account.getBalances().keySet();
                val balances = account.getBalances();
                balanceKeys.forEach(key -> {
                    map.put(key, balances.get(key).getValue());
                });
            }
            return null;
        }
    }

    @Override
    public Account getAccount(UInt160 address) {
    	return accountStore.get(address);
    }
    
    private BlockHeader initDB(WriteBatch batch){
        BlkTxMapping blkTxMapping = new BlkTxMapping(genesisBlock.id(), genesisBlock.getTransactionIds());
        blkTxMappingStore.set(genesisBlock.id(), blkTxMapping, batch);
        headerStore.set(genesisBlock.id(), genesisBlockHeader, batch);
        heightStore.set(genesisBlock.height(), genesisBlock.id(), batch);
        headBlkStore.set(new HeadBlock(genesisBlockHeader.getIndex(), genesisBlockHeader.id()), batch);
        prodStateStore.set(new ProducerStatus(1L), batch);
        return genesisBlockHeader;
    }

    public BlockHeader reInitDB(WriteBatch batch){
        headerStore.foreachForDelete(batch);
        heightStore.foreachForDelete(batch);
        blkTxMappingStore.foreachForDelete(batch);
        accountStore.foreachForDelete(batch);
        nameToAccountStore.foreachForDelete(batch);
        prodStateStore.delete(batch);
        headBlkStore.delete(batch);
        return initDB(batch);
    }

    public BlockHeader reInit(){
        val batch = db.getBatchWrite();
        val ret = reInitDB(batch);
        db.BatchWrite(batch);
        return ret;
    }

    public BlockHeader init(HeadBlock headBlock){
        val blockHeader = headerStore.get(headBlock.getId());
        if(blockHeader == null) return reInit();
        else return blockHeader;
    }

}