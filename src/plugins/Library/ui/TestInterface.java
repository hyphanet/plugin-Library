/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.ui;

import plugins.Library.search.Search;
import plugins.Library.*;

import freenet.library.util.exec.Execution;
import freenet.support.Logger;

import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TestInterface{
	static HashMap<String,Execution> requests = new HashMap<String,Execution>();
	static int requestcount =0;

	public static void main(String[] args){
		try{
			Logger.setupStdoutLogging(Logger.MINOR, "Strarting logging");
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String command;
			String param;
			String line;
			String index = "../../Freenet/myindex8";
			Library library = Library.init(null);
			Search.setup(library, null);

			do{
				line = br.readLine();
				command = line.substring(0, 1);
				param = line.substring(1);

				if("f".equals(command)){
					requests.put(""+requestcount,library.getIndex(index).getTermEntries(param));
					System.out.println("Started request "+requestcount);
					requestcount++;
				}
				if("s".equals(command)){
					Search search = Search.startSearch(param, index);
					if (search == null)
						System.out.println("Stopwords too prominent in query");
					else{
						requests.put(""+requestcount,search);
						System.out.println("Started request "+requestcount);
						requestcount++;
					}
				}
				if("p".equals(command))
					System.out.println(requests.get(param).toString());
				if("r".equals(command))
					System.out.println(requests.get(param).getResult());
				if("i".equals(command))
					System.out.println(library.getIndex(index));
//				if("w".equals(command))
//					WebUI.resultNodeGrouped(requests.get(param), true, true);
			}while(!"x".equals(command));
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
