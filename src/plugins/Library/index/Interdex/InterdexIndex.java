package plugins.Library.index.Interdex;

import plugins.XMLLibrarian.Request;
import plugins.Interdex.index.Index;

public class InterdexIndex extends plugins.XMLLibrarian.Index {
	Index index;

	public InterdexIndex(Index index){
		this.index = index;
	}

	@Override
	public Request find(String term) {
		throw new UnsupportedOperationException();
	}

}
