/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.index.xml;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Main Index Parser
 * 
 * Parser for master index <tt>index.xml</tt>. This does _NOT_ support index file generate by
 * XMLSpider earlier then version 8 (Sep-2007).
 * 
 * @author j16sdiz (1024D/75494252)
 */
public class MainIndexParser extends DefaultHandler {
	protected enum V1State {
		/** Anything else */
		MAIN,
		/** Inside &lt;header&gt; */
		HEADER,
		/** One-level inside &lt;header&gt;, such as &lt;title&gt; */
		HEADER_INNER,
		/** Inside &lt;keywords&gt; */
		KEYWORDS
	}

	protected class V1Handler extends DefaultHandler {
		V1State state;
		String headerKey;
		StringBuilder headerValue;

		@Override
		public void startDocument() {
			state = V1State.MAIN;
			headerKey = null;
			headerValue = null;
		}

		@Override
		public void characters(char ch[], int start, int length) throws SAXException {
			if (state != V1State.HEADER_INNER || headerKey == null)
				return;
			headerValue.append(ch, start, length);
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			switch (state) {
			case MAIN:
				mainStartElement(uri, localName, qName, attributes);
				break;
			case HEADER:
				headerStartElement(uri, localName, qName, attributes);
				break;
			case HEADER_INNER:
				headerInnerStartElement(uri, localName, qName, attributes);
				break;
			case KEYWORDS:
				keywordsStartElement(uri, localName, qName, attributes);
				break;
			}
		}

		private void mainStartElement(String uri, String localName, String qName, Attributes attributes)
		        throws SAXException {
			if ("main_index".equals(localName)) {
				state = V1State.MAIN;
			} else if ("header".equals(localName)) {
				state = V1State.HEADER;
			} else if ("keywords".equals(localName)) {
				state = V1State.KEYWORDS;
			} else if ("prefix".equals(localName)) {
				// ignore
			} else {
				throw new SAXException("Bad tag <" + localName + "> in main @" + location());
			}
		}

		private void headerStartElement(String uri, String localName, String qName, Attributes attributes) {
			headerKey = localName;
			headerValue = new StringBuilder();
			state = V1State.HEADER_INNER;
		}

		private void headerInnerStartElement(String uri, String localName, String qName, Attributes attributes)
		        throws SAXException {
			throw new SAXException("Bad tag <" + localName + "> in header @" + location());
		}

		private void keywordsStartElement(String uri, String localName, String qName, Attributes attributes)
		        throws SAXException {
			if (!"subIndex".equals(localName))
				throw new SAXException("Bad tag <" + localName + "> in keywords @" + location());

			String key = attributes.getValue("", "key");
			if (key == null || key.length() < 1 || key.length() > 32 || !key.matches("^[0-9a-fA-F]*$"))
				throw new SAXException("Bad <subIndex> tag @" + location());
			key = key.toLowerCase(Locale.US);
			subIndice.add(key);
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			switch (state) {
			case MAIN:
				mainEndElement(uri, localName, qName);
				break;
			case HEADER:
				headerEndElement(uri, localName, qName);
				break;
			case HEADER_INNER:
				headerInnerEndElement(uri, localName, qName);
				break;
			case KEYWORDS:
				keywordsEndElement(uri, localName, qName);
				break;
			}
		}

		private void mainEndElement(String uri, String localName, String qName) throws SAXException {
			if (!"main_index".equals(localName) && !"prefix".equals(localName))
				throw new SAXException("Bad close tag </" + localName + "> in main @" + location());
		}

		private void headerEndElement(String uri, String localName, String qName) throws SAXException {
			if (!"header".equals(localName))
				throw new SAXException("Bad close tag </" + localName + "> in header @" + location());

			state = V1State.MAIN;
		}

		private void headerInnerEndElement(String uri, String localName, String qName) throws SAXException {
			if (!localName.equals(headerKey))
				throw new SAXException("Bad close tag </" + localName + "> in header @" + location());
			header.put(headerKey, headerValue.toString());
			headerKey = null;
			headerValue = null;

			state = V1State.HEADER;
		}

		private void keywordsEndElement(String uri, String localName, String qName) throws SAXException {
			if ("subIndex".equals(localName)) {
				// ignore
			} else if ("keywords".equals(localName)) {
				state = V1State.MAIN;
			} else
				throw new SAXException("Bad close tag </" + localName + "> in keywords @" + location());
		}
	}

	protected int version;
	protected Map<String, String> header;
	protected Set<String> subIndice;
	protected Set<String> siteIndice;
	protected DefaultHandler handler;
	protected Locator locator;

	@Override
	public void characters(char ch[], int start, int length) throws SAXException {
		handler.characters(ch, start, length);
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		this.locator = locator;
	}

	@Override
	public void startDocument() {
		header = new HashMap<String, String>();
		subIndice = new HashSet<String>();
		siteIndice = new HashSet<String>();
		handler = null;
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (handler == null) {
			version = detectVersion(uri, localName, qName, attributes);

			switch (version) {
			case 1:
				handler = new V1Handler();
				break;
			default:
				throw new SAXException("Unsupported version @(" + location() + " : " + version);
			}

			handler.startDocument();
		}
		handler.startElement(uri, localName, qName, attributes);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		handler.endElement(uri, localName, qName);
	}

	@Override
	public void endDocument() throws SAXException {
		handler.endDocument();
	}

	private int detectVersion(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if ("main_index".equals(localName)) {
			return 1;
		} else {
			throw new SAXException("Unknown tag @" + location() + " : " + localName);
		}
	}

	private String location() {
		if (locator != null)
			return "(" + locator.getLineNumber() + "," + locator.getColumnNumber() + ")";
		else
			return "()";
	}

	public int getVersion() {
		return version;
	}

	public String getHeader(String key) {
		return header.get(key);
	}

	public Set<String> getSubIndice() {
		return subIndice;
	}

	public Set<String> getSiteIndice() {
		return siteIndice;
	}
}
