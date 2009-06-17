/**
 * 
 */
package plugins.XMLLibrarian;

public class URIWrapper implements Comparable<URIWrapper> {
	public String URI;
	public String descr;

	public int compareTo(URIWrapper o) {
		return URI.compareTo(o.URI);
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
