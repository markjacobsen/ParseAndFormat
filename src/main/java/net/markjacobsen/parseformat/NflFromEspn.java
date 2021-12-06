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
import com.cffreedom.utils.JsonUtils;
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
		
		//parseJson();
		parse2();
		
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
	
	private static void parseJson() {
		try {
			/*
			 * "season": {
			 * 		"year": 2021
			 * }
			 * "week": {
			 * 		"number": 13,
			 * 		...
			 * 		"events": [
			 * 			{
			 * 				...
			 * 				"shortName": "TB @ ATL"
			 * 				...
			 * 				"competitions": [
			 * 					{
			 * 						...
			 * 						"competitors": [
			 * 							{
			 * 								...
			 * 								"team": {
			 * 									"abbreviation": "ATL"
			 * 								}
			 * 								"score": "17"
			 * 							}
			 * 							{
			 * 								...
			 * 								"team": {
			 * 									"abbreviation": "TB"
			 * 								}
			 * 								"score": "20"
			 * 								"records": [
			 * 									{
			 * 										"type": "total"
			 * 										"summary": "8-3"
			 * 										
			 * 									}
			 * 								]
			 * 							}
			 * 						]
			 * 					}
			 * 				]
			 * 				...
			 * 				"status": {
			 * 					"type": {
			 * 						"shortDetail": "2:44 - 3rd"
			 * 					}
			 * 				}
			 * 				"broadcasts": [
			 * 					{
			 * 						"market": "national"
			 * 						"names": [
			 * 							"CBS"
			 * 						]
			 * 					}
			 * 				]
			 * 			}
			 * 		]
			 */
			String jsonResp = FileUtils.getFileContents(jsonRespFile);
			JSONObject json = JsonUtils.getJsonObject(jsonResp);
			
			JSONObject season = JsonUtils.getJsonObject(json, "season");
			Long yearNumber = JsonUtils.getLong(season, "year");
			
			JSONObject jsonWeek = JsonUtils.getJsonObject(json, "week");
			Long weekNumber = JsonUtils.getLong(jsonWeek, "number");
			logger.debug("Week: "+weekNumber);
			
			String weekFile = SystemUtils.getDirWork() + "\\nfl.scores."+yearNumber+".week."+String.format("%02d", weekNumber)+".json";
			FileUtils.copyFile(jsonRespFile, weekFile);
			
			JSONArray events = JsonUtils.getJsonArray(json, "events");
			logger.debug("Games this week: "+events.size());
			for (int x = 0; x < events.size(); x++) {
				// START: Game
				JSONObject event = (JSONObject)events.get(x);
				logger.debug("Game: "+JsonUtils.getString(event, "shortName"));
				JSONArray competitions = JsonUtils.getJsonArray(event, "competitions");
				for (int y = 0; y < competitions.size(); y++) {
					JSONObject competition = (JSONObject)competitions.get(y);
					
					JSONObject status = JsonUtils.getJsonObject(competition, "status");
					JSONObject statusType = JsonUtils.getJsonObject(status, "type");
					String timeLeft = JsonUtils.getString(statusType, "shortDetail");
					
					String broadcastOn = "";
					JSONArray broadcasts = JsonUtils.getJsonArray(competition, "broadcasts");
					for (int b = 0; b < broadcasts.size(); b++) {
						JSONObject broadcast = (JSONObject)broadcasts.get(b);
						JSONArray names = JsonUtils.getJsonArray(broadcast, "names");
						for (int n = 0; n < names.size(); n++) {
							String name = (String)names.get(n);
							if (Utils.hasLength(broadcastOn)) {
								broadcastOn += ", ";
							}
							broadcastOn += name;
						}
					}
					
					Calendar gmt = Convert.toCalendar(JsonUtils.getString(competition, "date"));
					Calendar local = DateTimeUtils.gmtToLocal(gmt);
					logger.debug("  "+Format.date(Format.DATE_HUMAN, local)+" on "+broadcastOn+"       "+timeLeft);
										
					JSONArray competitors = JsonUtils.getJsonArray(competition, "competitors");
					for (int z = 0; z < competitors.size(); z++) {
						JSONObject competitor = (JSONObject)competitors.get(z);
						String homeAway = JsonUtils.getString(competitor, "homeAway");
						
						JSONObject team = JsonUtils.getJsonObject(competitor, "team");
						String abbrev = JsonUtils.getString(team, "abbreviation");
						String score = JsonUtils.getString(competitor, "score");
						
						JSONArray records = JsonUtils.getJsonArray(competitor, "records");
						String recordOverall = "";
						String recordLocation = "";
						String location = "";
						for (int r = 0; r < records.size(); r++) {
							JSONObject record = (JSONObject)records.get(r);
							String type = JsonUtils.getString(record, "type");
							String summary = JsonUtils.getString(record, "summary");
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
						String formattedTeamScore = Format.pad("    "+abbrev+": "+score, 22, " ", true);
						logger.debug(formattedTeamScore+recordOverall+" overall, "+recordLocation+" "+location);
					}
				}
				// END: Game
			}
		} catch (ParseException | FileSystemException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void parse2() {
		List<String> output = new ArrayList<>();
		String jsonResp = FileUtils.getFileContents(jsonRespFile);
		DocumentContext jsonContext = JsonPath.parse(jsonResp);
		Integer week = jsonContext.read("week.number");
		List<Object> events = jsonContext.read("events");
		
		output.add("Week: "+week);
		output.add(events.size()+" games");
		
		for (int x = 0; x < events.size(); x++) {
			output.add(jsonContext.read("events["+x+"].shortName"));
			List<Object> competitions = jsonContext.read("events["+x+"].competitions");
			for (int y = 0; y < competitions.size(); y++) {
				
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
