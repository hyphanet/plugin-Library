package plugins.Library.util;

import freenet.support.Logger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class URIWrapper implements Comparable<URIWrapper> {
	public String URI;
	public String descr;
	public List<Integer> termpositions;

	public URIWrapper(){
	}
	private URIWrapper(URIWrapper copy){
		URI = copy.URI;
		descr = copy.descr;
		termpositions = copy.termpositions;
	}

	public int compareTo(URIWrapper o) {
		return URI.compareTo(o.URI);
	}
	
	/**
	 * Returns new URIWrapper if at least one position in this URIWrapper is directly followed by a position in o,
	 * a new URIWrapper is returned containing the postion of the second terms of matching phrases.
	 * null is returned if no cases are found
	 * @param o
	 * @return
	 */
	public URIWrapper followedby(URIWrapper o) throws Exception{
		if(compareTo(o)!=0)
			return null;
		if(termpositions == null || o.termpositions == null)
				throw new Exception("Term positions not recorded for these URIWrappers, cannot determine following");
		ArrayList<Integer> newtermpositions = new ArrayList<Integer>();
		// Loop through positions to find anywhere where positions in this URIWrapper are followed by those in o
		for (Iterator<Integer> it = termpositions.iterator(); it.hasNext();) {
			Integer integer = it.next()+1;
			if(o.termpositions.contains(integer)){
				newtermpositions.add(integer);
				Logger.minor(this, descr + "followed at "+ integer.toString());
			}
		}
		// If there are matches return a new URIWrapper of them
		if(newtermpositions.size()>0){
			URIWrapper result = new URIWrapper(this);
			result.termpositions = newtermpositions;
			return result;
		}else
			return null;
	}

	public boolean equals(Object o) {
		if (o == null || o.getClass() != getClass())
			return false;
		return URI.equals(((URIWrapper) o).URI);
	}

	public int hashCode() {
		return URI.hashCode();
	}
}
