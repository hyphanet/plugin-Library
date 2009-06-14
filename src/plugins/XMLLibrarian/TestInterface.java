package plugins.XMLLibrarian;


import java.util.HashMap;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class TestInterface{
	static HashMap<String,Request> requests = new HashMap<String,Request>();
	static int requestcount =0;
	
	public static void main(String[] args){
		try{
			Library lib = new Library();
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String[] command;
			
			do{
				command = br.readLine().split(" ");
				
				if("find".equals(command[0])){
					requests.put(""+requestcount,lib.findTerm("xml:index", command[1]));
					System.out.println("Started request "+requestcount);
					requestcount++;
				}
				if("progress".equals(command[0]))
					System.out.println(requests.get(command[1]).toString());
			}while(!"exit".equals(command[0]));
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
