/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package plugins.Library.index;

import plugins.Library.Library;
import plugins.Library.client.FreenetArchiver;
import plugins.Library.util.SkeletonTreeMap;
import plugins.Library.util.SkeletonBTreeMap;
import plugins.Library.util.SkeletonBTreeSet;
import plugins.Library.util.exec.ProgressParts;
import plugins.Library.util.exec.Progress;
import plugins.Library.util.exec.SimpleProgress;
import plugins.Library.util.exec.BaseCompositeProgress;
import plugins.Library.util.exec.TaskAbortException;
import plugins.Library.util.exec.TaskInProgressException;
import plugins.Library.io.serial.Serialiser.*;
import plugins.Library.io.serial.Serialiser;
import plugins.Library.io.serial.Translator;
import plugins.Library.io.serial.ProgressTracker;
import plugins.Library.io.serial.Archiver;
import plugins.Library.io.serial.IterableSerialiser;
import plugins.Library.io.serial.MapSerialiser;
import plugins.Library.io.serial.LiveArchiver;
import plugins.Library.io.serial.ParallelSerialiser;
import plugins.Library.io.serial.Packer;
import plugins.Library.io.serial.Packer.Scale; // WORKAROUND javadoc bug #4464323
import plugins.Library.io.serial.FileArchiver;
import plugins.Library.io.DataFormatException;
import plugins.Library.io.YamlReaderWriter;
import freenet.keys.FreenetURI;
import freenet.node.RequestStarter;
import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Date;

/**
 ** Serialiser for the components of a ProtoIndex.
 **
 ** DOCUMENT
 **
 ** @author infinity0
 */
public class ProtoIndexComponentSerialiser {

  final protected static HashMap<Integer, ProtoIndexComponentSerialiser> srl_fmt =
      new HashMap<Integer, ProtoIndexComponentSerialiser>();

  final protected static int FMT_FREENET_SIMPLE = 0x2db3c940;
  public final static int FMT_FILE_LOCAL = 0xd439e29a;

  public final static int FMT_DEFAULT = FMT_FREENET_SIMPLE;

  /**
   ** Converts between a low-level object and a byte stream.
   */
  final protected static YamlReaderWriter yamlrw = new YamlReaderWriter();

  /**
   ** Translator for the local entries of a node of the ''term table''.
   */
  final protected static Translator<SkeletonTreeMap<String, SkeletonBTreeSet<TermEntry>>, Map<String, Object>> ttab_keys_mtr =
      new TreeMapTranslator<String, SkeletonBTreeSet<TermEntry>>(null);

  /**
   ** Translator for the local entries of a node of the ''B-tree'' for a ''term''.
   */
  final protected static Translator<SkeletonTreeMap<TermEntry, TermEntry>, Collection<TermEntry>> term_data_mtr =
      new SkeletonBTreeSet.TreeSetTranslator<TermEntry>();

  /**
   ** Translator for {@link URIKey}.
   */
  final protected static Translator<URIKey, String> utab_keys_ktr =
      new Translator<URIKey, String>() {
        public String app(URIKey k) {
          return k.toString();
        }

        public URIKey rev(String s) {
          return new URIKey(URIKey.hexToBytes(s));
        }
      };

  /**
   ** Translator for the local entries of a node of the ''uri table''.
   */
  final protected static Translator<SkeletonTreeMap<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>, Map<String, Object>> utab_keys_mtr =
      new TreeMapTranslator<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(utab_keys_ktr);

  /**
   ** Serialiser for the ''targets'' of the values stored in a node of the ''B-tree'' for a ''term''.
   * In this case, the values are the actual targets and are stored inside the node, so we use a
   * dummy.
   */
  final protected static MapSerialiser<TermEntry, TermEntry> term_dummy =
      new DummySerialiser<TermEntry, TermEntry>();

  /**
   ** Serialiser for the ''targets'' of the values stored in a node of the ''B-tree'' for a
   * ''urikey''. In this case, the values are the actual targets and are stored inside the node, so
   * we use a dummy.
   */
  final protected static MapSerialiser<FreenetURI, URIEntry> uri_dummy =
      new DummySerialiser<FreenetURI, URIEntry>();

  /**
   ** {@link Scale} for the root node of the ''B-tree'' that holds ''term entries'' for a ''term''.
   */
  final protected static Packer.Scale<SkeletonBTreeSet<TermEntry>> term_data_scale =
      new Packer.Scale<SkeletonBTreeSet<TermEntry>>() {
        @Override
        public int weigh(SkeletonBTreeSet<TermEntry> element) {
          return element.sizeRoot();
        }
      };

  /**
   ** {@link Scale} for the root node of the ''B-tree'' that holds ''uri-entry mappings'' for a
   * ''urikey''.
   */
  final protected static Packer.Scale<SkeletonBTreeMap<FreenetURI, URIEntry>> uri_data_scale =
      new Packer.Scale<SkeletonBTreeMap<FreenetURI, URIEntry>>() {
        @Override
        public int weigh(SkeletonBTreeMap<FreenetURI, URIEntry> element) {
          return element.sizeRoot();
        }
      };

  /**
   ** Get the instance responsible for the given format ID.
   **
   ** @param archiver The LiveArchiver to use, can be null.
   ** @throws UnsupportedOperationException if the format ID is unrecognised
   ** @throws IllegalStateException if the requirements for creating the instance (eg. existence of a
   *         freenet node) are not met.
   */
  public synchronized static ProtoIndexComponentSerialiser get(int fmtid,
      LiveArchiver<Map<String, Object>, SimpleProgress> archiver) {
    ProtoIndexComponentSerialiser srl = srl_fmt.get(fmtid);
    if (srl == null || srl.leaf_arx != null && srl.leaf_arx != archiver) {
      srl = new ProtoIndexComponentSerialiser(fmtid, archiver);
      if (archiver == null)
        srl_fmt.put(fmtid, srl);
    }
    return srl;
  }

  /**
   ** Get the instance responsible for the default format ID.
   **
   ** @throws IllegalStateException if the requirements for creating the instance (eg. existence of a
   *         freenet node) are not met.
   */
  public static ProtoIndexComponentSerialiser get() {
    return get(FMT_DEFAULT, null);
  }

  /**
   ** Unique code for each instance of this class.
   */
  final protected int serialFormatUID;

  /**
   ** Archiver for the lowest level (leaf).
   */
  final protected LiveArchiver<Map<String, Object>, SimpleProgress> leaf_arx;

  public LiveArchiver<Map<String, Object>, SimpleProgress> getLeafSerialiser() {
    return leaf_arx;
  }

  /**
   ** Serialiser for the ''targets'' of the values stored in a node of the ''term table''. In this
   * case, each target is the root node of the ''B-tree'' that holds ''term entries'' for the
   * ''term'' mapping to the value.
   */
  final protected BTreePacker<String, SkeletonBTreeSet<TermEntry>, EntryGroupSerialiser<String, SkeletonBTreeSet<TermEntry>>> ttab_data;

  /**
   ** Serialiser for the ''targets'' of the values stored in a node of the ''uri table''. In this
   * case, each target is the root node of the ''B-tree'' that holds ''uri-entry mappings'' for the
   * ''urikey'' mapping to the value.
   */
  final protected BTreePacker<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>, EntryGroupSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>> utab_data;

  /**
   ** Constructs a new instance using the given format.
   * 
   * @param archiver
   **
   ** @throws UnsupportedOperationException if the format ID is unrecognised
   ** @throws IllegalStateException if the requirements for creating the instance (eg. existence of a
   *         freenet node) are not met.
   */
  protected ProtoIndexComponentSerialiser(int fmtid,
      LiveArchiver<Map<String, Object>, SimpleProgress> archiver) {
    if (archiver != null) {
      leaf_arx = archiver;
    } else {
      switch (fmtid) {
        case FMT_FREENET_SIMPLE:
          short priorityClass = RequestStarter.INTERACTIVE_PRIORITY_CLASS;
          if (archiver instanceof FreenetArchiver)
            priorityClass = ((FreenetArchiver) archiver).priorityClass;
          leaf_arx = Library.makeArchiver(yamlrw, ProtoIndex.MIME_TYPE,
              0x180 * ProtoIndex.BTREE_NODE_MIN, priorityClass);
          break;
        case FMT_FILE_LOCAL:
          leaf_arx = new FileArchiver<Map<String, Object>>(yamlrw, true,
              YamlReaderWriter.FILE_EXTENSION, "", "", null);
          break;
        default:
          throw new UnsupportedOperationException("Unknown serial format id");
      }
    }

    serialFormatUID = fmtid;

    ttab_data =
        new BTreePacker<String, SkeletonBTreeSet<TermEntry>, EntryGroupSerialiser<String, SkeletonBTreeSet<TermEntry>>>(
            new EntryGroupSerialiser<String, SkeletonBTreeSet<TermEntry>>(leaf_arx, null,
                new SkeletonBTreeSet.TreeTranslator<TermEntry, TermEntry>(null, term_data_mtr) {
                  @Override
                  public SkeletonBTreeSet<TermEntry> rev(Map<String, Object> tree)
                      throws DataFormatException {
                    return setSerialiserFor(super.rev(tree));
                  }
                }),
            term_data_scale, ProtoIndex.BTREE_ENT_MAX);

    utab_data =
        new BTreePacker<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>, EntryGroupSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>>(
            new EntryGroupSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>(leaf_arx, null,
                new SkeletonBTreeMap.TreeTranslator<FreenetURI, URIEntry>(null, null) {
                  @Override
                  public SkeletonBTreeMap<FreenetURI, URIEntry> rev(Map<String, Object> tree)
                      throws DataFormatException {
                    return setSerialiserFor(super.rev(tree));
                  }
                }),
            uri_data_scale, ProtoIndex.BTREE_ENT_MAX);
  }

  /**
   ** Set the serialisers for the ''uri table'' and the ''term table'' on an index.
   */
  public ProtoIndex setSerialiserFor(ProtoIndex index) {
    // set serialisers on the ttab
    BTreeNodeSerialiser<String, SkeletonBTreeSet<TermEntry>> ttab_keys =
        new BTreeNodeSerialiser<String, SkeletonBTreeSet<TermEntry>>("term listings", leaf_arx,
            index.ttab.makeNodeTranslator(null, ttab_keys_mtr));
    index.ttab.setSerialiser(ttab_keys, ttab_data);

    // set serialisers on the utab
    BTreeNodeSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>> utab_keys =
        new BTreeNodeSerialiser<URIKey, SkeletonBTreeMap<FreenetURI, URIEntry>>("uri listings",
            leaf_arx, index.utab.makeNodeTranslator(utab_keys_ktr, utab_keys_mtr));
    index.utab.setSerialiser(utab_keys, utab_data);

    // set serialiser on the index
    index.setSerialiser(this);
    return index;
  }

  /**
   ** Set the serialiser for the ''B-tree'' that holds the ''uri-entry mappings'' for a ''urikey''.
   */
  public SkeletonBTreeMap<FreenetURI, URIEntry> setSerialiserFor(
      SkeletonBTreeMap<FreenetURI, URIEntry> entries) {
    BTreeNodeSerialiser<FreenetURI, URIEntry> uri_keys =
        new BTreeNodeSerialiser<FreenetURI, URIEntry>("uri entries", leaf_arx,
            entries.makeNodeTranslator(null, null) // no translator needed as FreenetURI and
                                                   // URIEntry are both directly serialisable by
                                                   // YamlReaderWriter
        );
    entries.setSerialiser(uri_keys, uri_dummy);
    return entries;
  }

  /**
   ** Set the serialiser for the ''B-tree'' that holds the ''term entries'' for a ''term''.
   */
  public SkeletonBTreeSet<TermEntry> setSerialiserFor(SkeletonBTreeSet<TermEntry> entries) {
    BTreeNodeSerialiser<TermEntry, TermEntry> term_keys =
        new BTreeNodeSerialiser<TermEntry, TermEntry>("term entries", leaf_arx,
            entries.makeNodeTranslator(null, term_data_mtr));
    entries.setSerialiser(term_keys, term_dummy);
    return entries;
  }


  /************************************************************************
   ** Generic {@link SkeletonTreeMap} translator.
   **
   ** @author infinity0
   */
  public static class TreeMapTranslator<K, V> extends SkeletonTreeMap.TreeMapTranslator<K, V> {

    final protected Translator<K, String> ktr;

    public TreeMapTranslator(Translator<K, String> k) {
      ktr = k;
    }

    /* @Override **/ public Map<String, Object> app(SkeletonTreeMap<K, V> map) {
      return app(map, new TreeMap<String, Object>(), ktr);
    }

    /* @Override **/ public SkeletonTreeMap<K, V> rev(Map<String, Object> map)
        throws DataFormatException {
      return rev(map, new SkeletonTreeMap<K, V>(), ktr);
    }
  }


  /************************************************************************
   ** Serialiser for a general B-tree node. This can be used for both a {@link SkeletonBTreeMap} and
   * a {@link SkeletonBTreeSet}; they use the same node class.
   **
   ** @author infinity0
   */
  public static class BTreeNodeSerialiser<K, V>
      extends ParallelSerialiser<SkeletonBTreeMap<K, V>.SkeletonNode, SimpleProgress>
      implements Archiver<SkeletonBTreeMap<K, V>.SkeletonNode>,
      Serialiser.Translate<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>>,
      Serialiser.Composite<LiveArchiver<Map<String, Object>, SimpleProgress>> {

    final protected Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> trans;
    final protected LiveArchiver<Map<String, Object>, SimpleProgress> subsrl;

    protected String name;

    /**
     ** Constructs a new serialiser. A new one must be constructed for each BTree being serialised.
     **
     ** @param n Description of what the map stores. This is used in the progress report.
     ** @param t Translator for the node - create this using
     *        {@link SkeletonBTreeMap#makeNodeTranslator(Translator, Translator)}.
     */
    public BTreeNodeSerialiser(String n, LiveArchiver<Map<String, Object>, SimpleProgress> s,
        SkeletonBTreeMap<K, V>.NodeTranslator<?, ?> t) {
      super(new ProgressTracker<SkeletonBTreeMap<K, V>.SkeletonNode, SimpleProgress>(
          SimpleProgress.class));
      subsrl = s;
      trans = t;
      name = n;
    }

    /* @Override **/ public Translator<SkeletonBTreeMap<K, V>.SkeletonNode, Map<String, Object>> getTranslator() {
      return trans;
    }

    /* @Override **/ public LiveArchiver<Map<String, Object>, SimpleProgress> getChildSerialiser() {
      return subsrl;
    }

    /* @Override **/ public void pullLive(PullTask<SkeletonBTreeMap<K, V>.SkeletonNode> task,
        SimpleProgress p) throws TaskAbortException {
      p.enteredSerialiser();
      try {
        SkeletonBTreeMap<K, V>.GhostNode ghost = (SkeletonBTreeMap.GhostNode) task.meta;
        p.setSubject("Pulling " + name + ": " + ghost.getRange());
        PullTask<Map<String, Object>> serialisable =
            new PullTask<Map<String, Object>>(ghost.getMeta());
        subsrl.pullLive(serialisable, p);
        ghost.setMeta(serialisable.meta);
        task.data = trans.rev(serialisable.data);
        p.exitingSerialiser();
      } catch (RuntimeException e) {
        p.abort(new TaskAbortException("Could not pull B-tree node", e));
      } catch (DataFormatException e) {
        p.abort(new TaskAbortException("Could not pull B-tree node", e));
      }
    }

    /* @Override **/ public void pushLive(PushTask<SkeletonBTreeMap<K, V>.SkeletonNode> task,
        SimpleProgress p) throws TaskAbortException {
      p.enteredSerialiser();
      try {
        p.setSubject("Pushing " + name + ": " + task.data.getRange());
        Map<String, Object> intermediate = trans.app(task.data);
        PushTask<Map<String, Object>> serialisable =
            new PushTask<Map<String, Object>>(intermediate, task.meta);
        subsrl.pushLive(serialisable, p);
        task.meta = task.data.makeGhost(serialisable.meta);
        p.exitingSerialiser();
      } catch (RuntimeException e) {
        p.abort(new TaskAbortException("Could not push B-tree node", e));
      }
    }

  }


  /************************************************************************
   ** Serialiser for the entries contained within a B-tree node.
   **
   ** @author infinity0
   */
  public static class BTreePacker<K, V, S extends IterableSerialiser<Map<K, V>> & Serialiser.Trackable<Map<K, V>>>
      extends Packer<K, V> implements MapSerialiser<K, V>, Serialiser.Trackable<V> {

    final protected ProgressTracker<V, BaseCompositeProgress> tracker;
    final protected S subsrl;

    public BTreePacker(S s, Packer.Scale<V> sc, int cap) {
      super(s, sc, cap);
      subsrl = s;
      tracker = new ProgressTracker<V, BaseCompositeProgress>(BaseCompositeProgress.class);
    }

    /* @Override **/ public ProgressTracker<V, ? extends Progress> getTracker() {
      return tracker;
    }

    @Override
    protected void preprocessPullBins(Map<K, PullTask<V>> tasks,
        Collection<PullTask<Map<K, V>>> bintasks) {
      for (Map.Entry<K, PullTask<V>> en : tasks.entrySet()) {
        try {
          BaseCompositeProgress p = tracker.addPullProgress(en.getValue());
          p.setSubProgress(ProgressTracker.makePullProgressIterable(subsrl.getTracker(), bintasks));
          p.setEstimate(ProgressParts.TOTAL_FINALIZED);
          p.setSubject("Pulling root container for " + en.getKey());
        } catch (TaskInProgressException e) {
          throw new AssertionError(e);
        }
      }
    }

    @Override
    protected void preprocessPushBins(Map<K, PushTask<V>> tasks,
        Collection<PushTask<Map<K, V>>> bintasks) {
      for (Map.Entry<K, PushTask<V>> en : tasks.entrySet()) {
        try {
          BaseCompositeProgress p = tracker.addPushProgress(en.getValue());
          p.setSubProgress(ProgressTracker.makePushProgressIterable(subsrl.getTracker(), bintasks));
          p.setEstimate(ProgressParts.TOTAL_FINALIZED);
          p.setSubject("Pushing root container for " + en.getKey());
        } catch (TaskInProgressException e) {
          throw new AssertionError(e);
        }
      }
    }

  }


  /************************************************************************
   ** Serialiser for the bins containing (B-tree root nodes) that was packed by the
   * {@link BTreePacker}.
   **
   ** @author infinity0
   */
  public static class EntryGroupSerialiser<K, V> extends
      ParallelSerialiser<Map<K, V>, SimpleProgress> implements IterableSerialiser<Map<K, V>>,
      Serialiser.Composite<LiveArchiver<Map<String, Object>, SimpleProgress>> {

    final protected LiveArchiver<Map<String, Object>, SimpleProgress> subsrl;
    final protected Translator<K, String> ktr;
    final protected Translator<V, Map<String, Object>> btr;

    public EntryGroupSerialiser(LiveArchiver<Map<String, Object>, SimpleProgress> s,
        Translator<K, String> k, Translator<V, Map<String, Object>> b) {
      super(new ProgressTracker<Map<K, V>, SimpleProgress>(SimpleProgress.class));
      subsrl = s;
      ktr = k;
      btr = b;
    }

    /* @Override **/ public LiveArchiver<Map<String, Object>, SimpleProgress> getChildSerialiser() {
      return subsrl;
    }

    /* @Override **/ public void pullLive(PullTask<Map<K, V>> task, SimpleProgress p)
        throws TaskAbortException {
      p.enteredSerialiser();
      try {
        p.setSubject("Pulling root container " + task.meta);
        PullTask<Map<String, Object>> t = new PullTask<Map<String, Object>>(task.meta);
        subsrl.pullLive(t, p);

        Map<K, V> map = new HashMap<K, V>(t.data.size() << 1);
        try {
          for (Map.Entry<String, Object> en : t.data.entrySet()) {
            map.put((ktr == null) ? (K) en.getKey() : ktr.rev(en.getKey()),
                btr.rev((Map<String, Object>) en.getValue()));
          }
        } catch (ClassCastException e) {
          // FIXME NORM more meaningful error message
          throw new DataFormatException("Exception in converting data", e, t.data, null, null);
        }

        task.data = map;
        p.exitingSerialiser();
      } catch (RuntimeException e) {
        p.abort(new TaskAbortException("Failed task: " + p.getSubject(), e));
      } catch (DataFormatException e) {
        p.abort(new TaskAbortException("Failed task: " + p.getSubject(), e));
      }
    }

    /* @Override **/ public void pushLive(PushTask<Map<K, V>> task, SimpleProgress p)
        throws TaskAbortException {
      p.enteredSerialiser();
      try {
        p.setSubject("Pushing root container for keys " + task.data);
        Map<String, Object> conv = new HashMap<String, Object>();
        for (Map.Entry<K, V> mp : task.data.entrySet()) {
          conv.put((ktr == null) ? (String) mp.getKey() : ktr.app(mp.getKey()),
              btr.app(mp.getValue()));
        }
        PushTask<Map<String, Object>> t = new PushTask<Map<String, Object>>(conv, task.meta);
        subsrl.pushLive(t, p);
        task.meta = t.meta;
        p.exitingSerialiser();
      } catch (RuntimeException e) {
        p.abort(new TaskAbortException("Failed task: " + p.getSubject(), e));
      }
    }

  }


  /************************************************************************
   ** Dummy serialiser.
   **
   ** A SkeletonBTreeMap node was designed to hold values externally, whereas (a B-tree node in the
   * (BTreeSet container for a term's results)) and (a B-tree node in the (BTreeMap container for a
   * uri's results)) hold them internally. This class simplifies the logic required; see the source
   * code of {@link SkeletonBTreeMap} for more details.
   **
   ** @deprecated Avoid using this class. It will be removed after option 1 (see the source code of
   *             {@link SkeletonBTreeMap}) is coded.
   ** @author infinity0
   */
  public static class DummySerialiser<K, T> implements IterableSerialiser<T>, MapSerialiser<K, T> {


    public void pull(PullTask<T> task) {
      task.data = (T) task.meta;
    }

    public void push(PushTask<T> task) {
      task.meta = task.data;
    }

    public void pull(Iterable<PullTask<T>> tasks) {
      for (PullTask<T> task : tasks) {
        task.data = (T) task.meta;
      }
    }

    public void push(Iterable<PushTask<T>> tasks) {
      for (PushTask<T> task : tasks) {
        task.meta = task.data;
      }
    }

    public void pull(Map<K, PullTask<T>> tasks, Object mapmeta) {
      for (PullTask<T> task : tasks.values()) {
        if (task.meta != null)
          task.data = (T) task.meta;
      }
    }

    public void push(Map<K, PushTask<T>> tasks, Object mapmeta) {
      for (PushTask<T> task : tasks.values()) {
        if (task.data != null)
          task.meta = task.data;
      }
    }

  }



}
