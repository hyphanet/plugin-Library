/*
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package plugins.Library.ui;

import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermPageEntry;

/**
 * TODO make this fit the TermEntry contract
 * 
 * @author MikeB
 */
public class TermPageGroupEntry extends TermEntry {
  private final SortedMap<Long, SortedSet<TermPageEntry>> editions =
      new TreeMap<Long, SortedSet<TermPageEntry>>();

  TermPageGroupEntry(String sitebase) {
    super(sitebase, 0);
  }

  @Override
  public TermEntry.EntryType entryType() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public boolean equalsTarget(TermEntry entry) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public void addPage(long uskEdition, TermPageEntry pageEntry) {
    // Add Edition
    if (!editions.containsKey(uskEdition))
      editions.put(uskEdition, new TreeSet(RelevanceComparator.comparator));
    editions.get(uskEdition).add(pageEntry);

    // TODO HIGH rework this... TermEntry is supposed to be immutable
    // if(rel < pageEntry.rel) // TODO enter better algorithm for calculating relevance here
    // rel = pageEntry.rel; // relevance should be on a per-edition basis, probably shouldnt use
    // TermEntry at all
  }

  SortedMap<Long, SortedSet<TermPageEntry>> getEditions() {
    return Collections.unmodifiableSortedMap(editions);
  }
}
