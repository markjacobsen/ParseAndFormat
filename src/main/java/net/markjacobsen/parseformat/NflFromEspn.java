package net.markjacobsen.parseformat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cffreedom.beans.Response;
import com.cffreedom.exceptions.FileSystemException;
import com.cffreedom.exceptions.NetworkException;
import com.cffreedom.utils.Convert;
import com.cffreedom.utils.DateTimeUtils;
import com.cffreedom.utils.Format;
import com.cffreedom.utils.SystemUtils;
import com.cffreedom.utils.Utils;
import com.cffreedom.utils.file.FileUtils;
import com.cffreedom.utils.net.HttpUtils;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * Download and process the JSON response for NFL scores
 * Found from here: https://gist.github.com/akeaswaran/b48b02f1c94f873c6655e7129910fc3b
 * 
 * @author MarkJacobsen.net
 *
 */
public class NflFromEspn {
	private static final Logger logger = LoggerFactory.getLogger(NflFromEspn.class);
	private static final String jsonRespFile = SystemUtils.getDirWork() + "\\nfl.scores.json";
	
	public static void main(String[] args) {
		//downloadCurrentWeek();
		
		parseJson();
		
		logger.info("done");
	}
	
	private static void downloadCurrentWeek() {
		try {
			Response resp = HttpUtils.httpGet("http://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard");
			FileUtils.writeStringToFile(jsonRespFile, resp.getDetail(), false);
		} catch (NetworkException | FileSystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	/*
	 * "season": {
	 * 		"year": 2021
	 * }
	 * "week": {
	 * 		"number": 13,
	 * 		...
	 * 	"events": [
	 * 		{
	 * 			...
	 * 			"shortName": "TB @ ATL"
	 * 			...
	 * 			"competitions": [
	 * 				{
	 * 					...
	 * 					"competitors": [
	 * 						{
	 * 							...
	 * 							"team": {
	 * 								"abbreviation": "ATL"
	 * 							}
	 * 							"score": "17"
	 * 						}
	 * 						{
	 * 							...
	 * 							"team": {
	 * 								"abbreviation": "TB"
	 * 							}
	 * 							"score": "20"
	 * 							"records": [
	 * 								{
	 * 									"type": "total"
	 * 									"summary": "8-3"
	 * 									
	 * 								}
	 * 							]
	 * 						}
	 * 					]
	 * 				}
	 * 			]
	 * 			...
	 * 			"status": {
	 * 				"type": {
	 * 					"shortDetail": "2:44 - 3rd"
	 * 				}
	 * 			}
	 * 			"broadcasts": [
	 * 				{
	 * 					"market": "national"
	 * 					"names": [
	 * 						"CBS"
	 * 					]
	 * 				}
	 * 			]
	 * 		}
	 * 	]
	 */
	private static void parseJson() {
		List<String> output = new ArrayList<>();
		String jsonResp = FileUtils.getFileContents(jsonRespFile);
		DocumentContext jsonContext = JsonPath.parse(jsonResp);
		Integer week = jsonContext.read("week.number");
		List<Object> events = jsonContext.read("events");
		
		output.add("Week: "+week);
		output.add(events.size()+" games");
		
		for (int x = 0; x < events.size(); x++) {
			String game = jsonContext.read("events["+x+"].shortName");
			output.add(game);
			List<Object> competitions = jsonContext.read("events["+x+"].competitions");
			for (int y = 0; y < competitions.size(); y++) {
				List<String> broadcasts = jsonContext.read("events["+x+"].competitions["+y+"].broadcasts[0].names");
				String broadcastOn = "";
				for (String name : broadcasts) {
					if (Utils.hasLength(broadcastOn)) {
						broadcastOn += ", ";
					}
					broadcastOn += name;
				}
				
				String timeLeft = jsonContext.read("events["+x+"].competitions["+y+"].status.type.shortDetail");
				String gameDate = jsonContext.read("events["+x+"].competitions["+y+"].date");
				Calendar gmt = Convert.toCalendar(gameDate);
				Calendar local = DateTimeUtils.gmtToLocal(gmt);
				output.add("  "+Format.date(Format.DATE_HUMAN, local)+" on "+broadcastOn+"       "+timeLeft);
				
				List<Object> competitors = jsonContext.read("events["+x+"].competitions["+y+"].competitors");
				for (int z = 0; z < competitors.size(); z++) {
					String team = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].team.abbreviation");
					String homeAway = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].homeAway");
					String score = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].score");
					
					List<Object> records = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records");
					String recordOverall = "";
					String recordLocation = "";
					String location = "";
					for (int r = 0; r < records.size(); r++) {
						String type = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records["+r+"].type");
						String summary = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records["+r+"].summary");
						if (type.equalsIgnoreCase("total")) {
							recordOverall = summary+" ("+getWinPct(summary)+")";
						} else if (type.equalsIgnoreCase("home") && homeAway.equalsIgnoreCase("home")) {
							recordLocation = summary+" ("+getWinPct(summary)+")";
							location = "at home";
						} else if (type.equalsIgnoreCase("road") && homeAway.equalsIgnoreCase("away")) {
							recordLocation = summary+" ("+getWinPct(summary)+")";
							location = "on the road";
						}
					}
					String formattedTeamScore = Format.pad("    "+team+": "+score, 22, " ", true);
					output.add(formattedTeamScore+recordOverall+" overall, "+recordLocation+" "+location);
				}
			}
		}
		
		for (String line : output) {
			System.out.println(line);
		}
	}
	
	private static String getWinPct(String record) {
		String[] wl = record.split("-");
		double w = Convert.toDouble(wl[0]);
		double l = Convert.toDouble(wl[1]);
		double total = w + l;
		double pct = (w / total) * 100;
		return Format.number(pct, 0)+"%";
	}
}
