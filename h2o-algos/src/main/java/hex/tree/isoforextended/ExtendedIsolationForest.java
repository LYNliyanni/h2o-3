package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.tree.CompressedTree;
import hex.tree.SharedTree;
import hex.tree.isoforextended.isolationtree.CompressedIsolationTree;
import hex.tree.isoforextended.isolationtree.IsolationTree;
import org.apache.log4j.Logger;
import water.DKV;
import water.H2O;
import water.Job;
import water.Key;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.util.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends ModelBuilder<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    transient private static final Logger LOG = Logger.getLogger(ExtendedIsolationForest.class);
    public static final int MAX_NTREES = 100_000; // todo valenad consult the size
    public static final int MAX_SAMPLE_SIZE = 100_000; // todo valenad consult the size
    
    private ExtendedIsolationForestModel _model;
    private long currentModelSize = 0;
    transient Random _rand;

    // Called from an http request
    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Key<ExtendedIsolationForestModel> key) {
        super(parms, key);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public ExtendedIsolationForest(boolean startup_once) {
        super(new ExtendedIsolationForestModel.ExtendedIsolationForestParameters(), startup_once);
    }
    
    @Override
    protected void checkMemoryFootPrint_impl() {
        long estimatedMemory = (5 * Double.BYTES * _train.numCols() * _parms._sample_size);
        estimatedMemory += currentModelSize;
        LOG.info(PrettyPrint.bytes(estimatedMemory) + " <> " + PrettyPrint.bytes(H2O.SELF._heartbeat.get_free_mem()));
        if (estimatedMemory >= H2O.SELF._heartbeat.get_free_mem() || estimatedMemory < 0) {
            error("sample_size", "Se ti to nevejde vole!");
        }
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            long extensionLevelMax = _parms.train().numCols() - 1;
            if (_parms._extension_level < 0 || _parms._extension_level > extensionLevelMax) {
                error("extension_level", "Parameter extension_level must be in interval [0, "
                        + extensionLevelMax + "] but it is " + _parms._extension_level);
            }
            if (_parms._sample_size < 0 || _parms._sample_size > MAX_SAMPLE_SIZE) {
                error("sample_size","Parameter sample_size must be in interval [0, "
                        + MAX_SAMPLE_SIZE + "] but it is " + _parms._sample_size);
            }
            if(_parms._ntrees < 0 || _parms._ntrees > MAX_NTREES)
                error("ntrees", "Parameter ntrees must be in interval [1, "
                        + MAX_NTREES + "] but it is " + _parms._ntrees);
        }
        if (expensive && error_count() == 0) checkMemoryFootPrint();
    }

    @Override
    protected Driver trainModelImpl() {
        return new ExtendedIsolationForestDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.AnomalyDetection
        };
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        public void computeImpl() {
            _model = null;
            try {
                init(true);
                if(error_count() > 0)
                    throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ExtendedIsolationForest.this);
    
                _model = new ExtendedIsolationForestModel(dest(), _parms,
                        new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
                
                buildIsolationTreeEnsemble();
            } finally {
                if(_model!=null)
                    _model.unlock(_job);
            }
        }

        private void buildIsolationTreeEnsemble() {
                _rand = RandomUtils.getRNG(_parms._seed);
                _model.delete_and_lock(_job); // todo valenad what is it good for?
                _model._output._iTrees = new CompressedIsolationTree[_parms._ntrees];
    
                int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
                
//                long treeSize = 0;
//                long startsWithMemory = H2O.CLOUD.free_mem();
                IsolationTree isolationTree = new IsolationTree(heightLimit, _parms._extension_level);
                for (int tid = 0; tid < _parms._ntrees; tid++) {
                    LOG.info("Starting tree: " + (tid + 1));
                    Timer timer = new Timer();
                    int randomUnit = _rand.nextInt();
                    Frame subSample = SamplingUtils.sampleOfFixedSize(_train, _parms._sample_size, _parms._seed + randomUnit);
                    double[][] subSampleArray = FrameUtils.asDoubles(subSample);
                    _model._output._iTrees[tid] = isolationTree.buildTree(subSampleArray, _parms._seed + _rand.nextInt(), tid);
//                    int treeSizeL = convertToBytes(isolationTree).length;
//                    LOG.info("Tree size: " + (treeSizeL/1_000_000.0));
                    _job.update(1);
                    currentModelSize += convertToBytes(_model._output._iTrees[tid]).length;
                    LOG.info("Model size: " + (currentModelSize/1_000_000.0));
//                    treeSize += treeSizeL;
//                    isolationTree.nodesTotalSize();
//                    if (startsWithMemory < H2O.CLOUD.free_mem()) {
//                        startsWithMemory = H2O.CLOUD.free_mem();
//                    }
//                    DKV.remove(subSample._key);
                    LOG.info((tid + 1) + ". tree was built in " + timer.toString() + ". Free memory: " + PrettyPrint.bytes(H2O.SELF._heartbeat.get_free_mem()));
                    LOG.info(H2O.STOREtoString());
                    checkMemoryFootPrint();
                    if (error_count() > 0)
                        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(ExtendedIsolationForest.this);
                }
    
                LOG.info("Model size: " + (currentModelSize/1_000_000.0) + " " + PrettyPrint.bytes(currentModelSize));
//                LOG.info("Trees size average: " + PrettyPrint.bytes(treeSize / _parms._ntrees));
//                LOG.info("Trees total size: " + PrettyPrint.bytes(treeSize));
//                LOG.info("Starts with mem: " + PrettyPrint.bytes(startsWithMemory) + " Ends with mem: " + PrettyPrint.bytes(H2O.CLOUD.free_mem()) + " Real memory usage: " + PrettyPrint.bytes(startsWithMemory - H2O.CLOUD.free_mem()));
//                LOG.info("Estimation memory usage: " + PrettyPrint.bytes(treeSize));
    
//                model.unlock(_job); // todo valenad what is it good for?
                _model._output._model_summary = createModelSummaryTable();
        }
    }

    public TwoDimTable createModelSummaryTable() {
        List<String> colHeaders = new ArrayList<>();
        List<String> colTypes = new ArrayList<>();
        List<String> colFormat = new ArrayList<>();

        colHeaders.add("Number of Trees"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Size of Subsample"); colTypes.add("int"); colFormat.add("%d");
        colHeaders.add("Extension Level"); colTypes.add("int"); colFormat.add("%d");

        final int rows = 1;
        TwoDimTable table = new TwoDimTable(
                "Model Summary", null,
                new String[rows],
                colHeaders.toArray(new String[0]),
                colTypes.toArray(new String[0]),
                colFormat.toArray(new String[0]),
                "");
        int row = 0;
        int col = 0;
        table.set(row, col++, _parms._ntrees);
        table.set(row, col++, _parms._sample_size);
        table.set(row, col, _parms._extension_level);
        return table;
    }

    private byte[] convertToBytes(Object object){
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(object);
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
    
}
