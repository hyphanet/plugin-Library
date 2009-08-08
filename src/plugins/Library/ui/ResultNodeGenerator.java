
package plugins.Library.ui;


import freenet.keys.FreenetURI;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import plugins.Library.index.TermEntry;
import plugins.Library.index.TermIndexEntry;
import plugins.Library.index.TermPageEntry;
import plugins.Library.index.TermTermEntry;


/**
 * Class for parsing and formatting search results
 *
 * @author MikeB
 */
public class ResultNodeGenerator {
	TreeMap<String, SortedMap<Long, SortedSet<TermPageEntry>>> groupmap;
	TreeSet<TermPageEntry> pageset;
	TreeSet<TermTermEntry> relatedTerms;
	TreeSet<TermIndexEntry> relatedIndexes;


	/**
	 * Parse result into generator
	 */
	public ResultNodeGenerator(Set<TermEntry> result, boolean group){
		if(group)
			groupmap = new TreeMap();
		else
			pageset = new TreeSet();
		relatedTerms = new TreeSet();
		relatedIndexes = new TreeSet();

		Iterator<TermEntry> it = result.iterator();
		while(it.hasNext()){
			TermEntry o = (TermEntry)it.next();
			if(o instanceof TermPageEntry){
				TermPageEntry pageEntry = (TermPageEntry)o;
				if(group){
					String sitebase;
					Long uskVersion = Long.valueOf(0);
					// Get the key and name
					FreenetURI uri;
					uri = pageEntry.getURI();
					uskVersion=Long.MIN_VALUE;
					// convert usk's
					if(uri.isSSKForUSK()){
						uri = uri.uskForSSK();
						// Get the USK edition
						uskVersion = uri.getEdition();
					}
					// Get the site base name, key + documentname - uskversion
					sitebase = uri.setMetaString(null).setSuggestedEdition(0).toString().replaceFirst("/0", "");
					Logger.minor(this, sitebase);

					// Add site
					if(!groupmap.containsKey(sitebase))
						groupmap.put(sitebase, new TreeMap<Long, SortedSet<TermPageEntry>>());
					SortedMap<Long, SortedSet<TermPageEntry>> sitemap = (SortedMap<Long, SortedSet<TermPageEntry>>)groupmap.get(sitebase);
					// Add Edition
					if(!sitemap.containsKey(uskVersion))
						sitemap.put(uskVersion, new TreeSet());
					// Add page
					sitemap.get(uskVersion).add(pageEntry);
				}else
					pageset.add(pageEntry);

			}else if(o instanceof TermTermEntry){
				relatedTerms.add((TermTermEntry)o);
			}else if(o instanceof TermIndexEntry){
				relatedIndexes.add((TermIndexEntry)o);
			}else
				Logger.error(this, "Unknown TermEntry type : "+o.getClass().getName());
			
		}
    }
	
	public HTMLNode generateIndexEntryNode(){
		return new HTMLNode("#", "TermIndexEntry code not done yet");
	}

	public HTMLNode generateTermEntryNode(){
		return new HTMLNode("#", "TermTermEntry code not done yet");
	}

	/**
	 * Generate node of page results from this generator
	 */
	public HTMLNode generatePageEntryNode(boolean showold, boolean js){
		HTMLNode pageListNode = new HTMLNode("div", "id", "results");

		int results = 0;
		// Loop to separate results into SSK groups
		
		if(groupmap != null){
			// Loop over keys
			Iterator<String> it2 = groupmap.keySet().iterator();
			while (it2.hasNext()) {
				String keybase = it2.next();
				SortedMap<Long, SortedSet<TermPageEntry>> siteMap = groupmap.get(keybase);
				HTMLNode siteNode = pageListNode.addChild("div", "style", "padding: 6px;");
				// Create a block for old versions of this SSK
				HTMLNode siteBlockOldOuter = siteNode.addChild("div", new String[]{"id", "style"}, new String[]{"result-hiddenblock-"+keybase, (!showold?"display:none":"")});
				// put title on block if it has more than one version in it
				if(siteMap.size()>1)
					siteBlockOldOuter.addChild("a", new String[]{"onClick", "name"}, new String[]{"toggleResult('"+keybase+"')", keybase}).addChild("h3", "class", "result-grouptitle", keybase.replaceAll("\\b.*/(.*)", "$1"));
				// inner block for old versions to be hidden
				HTMLNode oldEditionContainer = siteBlockOldOuter.addChild("div", new String[]{"class", "style"}, new String[]{"result-hideblock", "border-left: thick black;"});
				// Loop over all editions in this site
				Iterator<Long> it3 = siteMap.keySet().iterator();
				while(it3.hasNext()){
					Long version = it3.next();
					boolean newestVersion = !it3.hasNext();
					if(newestVersion)	// put older versions in block, newest outside block
						oldEditionContainer = siteNode;
					HTMLNode versionCell;
					HTMLNode versionNode;
					if(siteMap.get(version).size()>1||siteMap.size()>1){
						// table for this version
						versionNode = oldEditionContainer.addChild("table", new String[]{"class"}, new String[]{"librarian-result"});
						HTMLNode grouptitle = versionNode.addChild("tr").addChild("td", new String[]{"padding", "colspan"}, new String[]{"0", "3"});
						grouptitle.addChild("h4", "class", (newestVersion?"result-editiontitle-new":"result-editiontitle-old"), keybase.replaceAll("\\b.*/(.*)", "$1")+(version.longValue()>=0 ? "-"+version.toString():""));
						// Put link to show hidden older versions block if necessary
						if(newestVersion && !showold && js && siteMap.size()>1)
							grouptitle.addChild("a", new String[]{"href", "onClick"}, new String[]{"#"+keybase, "toggleResult('"+keybase+"')"}, "       ["+(siteMap.size()-1)+" older matching versions]");
						HTMLNode versionrow = versionNode.addChild("tr");
						versionrow.addChild("td", "width", "8px");
						// draw black line down the side of the version
						versionrow.addChild("td", new String[]{"class"}, new String[]{"sskeditionbracket"});

						versionCell=versionrow.addChild("td", "style", "padding-left:15px");
					}else
						versionCell = oldEditionContainer;
					// loop over each result in this version
					Iterator<TermPageEntry> it4 = siteMap.get(version).iterator();
					while(it4.hasNext()){
						versionCell.addChild(termPageEntryNode(it4.next(), newestVersion));
					}
				}
			}
		}else{
			for (Iterator<TermPageEntry> it = pageset.iterator(); it.hasNext();) {
				TermPageEntry termPageEntry = it.next();
				pageListNode.addChild("div").addChild(termPageEntryNode(termPageEntry, true));
			}
		}
		pageListNode.addChild("p").addChild("span", "class", "librarian-summary-found", "Found"+results+"results");
		return pageListNode;
	}
	
	private HTMLNode termPageEntryNode(TermPageEntry entry,boolean newestVersion) {
		FreenetURI uri = entry.getURI();
		String showtitle = entry.getTitle();
		String showurl = uri.toShortString();
		if (showtitle == null || showtitle.trim().length() == 0 || showtitle.equals("not available")) {
			showtitle = showurl;
		}
		String realurl = "/" + uri.toString();
		HTMLNode pageNode = new HTMLNode("div", new String[]{"class", "style"}, new String[]{"result-entry", ""});
		pageNode.addChild("a", new String[]{"href", "class", "style", "title"}, new String[]{realurl, "result-title", "color: " + (newestVersion ? "Blue" : "LightBlue"), uri.toString()}, showtitle);
		// create usk url
		if (uri.isSSKForUSK()) {
			String realuskurl = "/" + uri.uskForSSK().toString();
			pageNode.addChild("a", new String[]{"href", "class"}, new String[]{realuskurl, "result-uskbutton"}, "[ USK ]");
		}
		pageNode.addChild("br");
		pageNode.addChild("a", new String[]{"href", "class", "style"}, new String[]{realurl, "result-url", "color: " + (newestVersion ? "Green" : "LightGreen")}, showurl);
		
		return pageNode;
	}
}