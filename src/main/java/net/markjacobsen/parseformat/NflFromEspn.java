package net.markjacobsen.parseformat;

import java.util.Calendar;

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

public class NflFromEspn {
	private static final Logger logger = LoggerFactory.getLogger(NflFromEspn.class);
	private static final String jsonRespFile = SystemUtils.getDirWork() + "\\nfl.json";
	
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
	
	private static void parseJson() {
		try {
			/*
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
			JSONObject jsonWeek = JsonUtils.getJsonObject(json, "week");
			logger.debug("Week: "+JsonUtils.getLong(jsonWeek, "number"));
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
						for (int r = 0; r < records.size(); r++) {
							JSONObject record = (JSONObject)records.get(r);
							String type = JsonUtils.getString(record, "type");
							String summary = JsonUtils.getString(record, "summary");
							if (type.equalsIgnoreCase("total")) {
								recordOverall = summary;
							} else if (type.equalsIgnoreCase("home") && homeAway.equalsIgnoreCase("home")) {
								recordLocation = summary+" at home";
							} else if (type.equalsIgnoreCase("road") && homeAway.equalsIgnoreCase("away")) {
								recordLocation = summary+" on the road";
							}
						}
						String formattedTeamScore = Format.pad("    "+abbrev+": "+score, 22, " ", true);
						logger.debug(formattedTeamScore+recordOverall+" overall, "+recordLocation);
					}
				}
				// END: Game
			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
