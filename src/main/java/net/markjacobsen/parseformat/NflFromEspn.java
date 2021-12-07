package net.markjacobsen.parseformat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
		NflFromEspn nfl = new NflFromEspn();
		nfl.downloadCurrentWeek();
		
		nfl.parseJson();
		
		logger.info("done");
	}

	private void downloadCurrentWeek() {
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
	private void parseJson() {
		List<Game> games = new ArrayList<>();
		String jsonResp = FileUtils.getFileContents(jsonRespFile);
		DocumentContext jsonContext = JsonPath.parse(jsonResp);
		Integer week = jsonContext.read("week.number");
		List<Object> events = jsonContext.read("events");
				
		for (int x = 0; x < events.size(); x++) {
			Game game = new Game();
			game.title = jsonContext.read("events["+x+"].shortName");
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
				game.broadcasts = broadcastOn;
				
				game.status = jsonContext.read("events["+x+"].competitions["+y+"].status.type.shortDetail");
				String gameDate = jsonContext.read("events["+x+"].competitions["+y+"].date");
				Calendar gmt = Convert.toCalendar(gameDate);
				game.dateTime = DateTimeUtils.gmtToLocal(gmt);
				//output.add("  "+Format.date(Format.DATE_HUMAN, local)+" on "+broadcastOn+"       "+timeLeft);
				
				List<Object> competitors = jsonContext.read("events["+x+"].competitions["+y+"].competitors");
				for (int z = 0; z < competitors.size(); z++) {
					Team team = new Team();
					team.title = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].team.abbreviation");
					
					String homeAway = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].homeAway");
					String score = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].score");
					
					List<Object> records = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records");
					for (int r = 0; r < records.size(); r++) {
						String type = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records["+r+"].type");
						String summary = jsonContext.read("events["+x+"].competitions["+y+"].competitors["+z+"].records["+r+"].summary");
						if (type.equalsIgnoreCase("total")) {
							team.overallRecord = summary;
						} else if (type.equalsIgnoreCase("home") && homeAway.equalsIgnoreCase("home")) {
							team.homeRecord = summary;
						} else if (type.equalsIgnoreCase("road") && homeAway.equalsIgnoreCase("away")) {
							team.awayRecord = summary;
						}
					}
					
					if (homeAway.equalsIgnoreCase("home")) {
						game.homeTeam = team;
						game.homeTeamScore = Convert.toInt(score, false);
					} else {
						game.awayTeam = team;
						game.awayTeamScore = Convert.toInt(score, false);
					}
				}
			}
			games.add(game);
		}
		
		Comparator<Game> compareator = Comparator
						                .comparing(Game::getDateTimeCompareVal)
						                .thenComparing(Game::getTitleCompareVal);
		
		List<Game> sortedGames = games.stream()
						                .sorted(compareator)
						                .collect(Collectors.toList());
		
		System.out.println("Week: "+week);
		System.out.println(games.size()+" games");
		for (Game game : sortedGames) {
			game.print();;
		}
	}
	
	class Game {
		String title;
		Calendar dateTime;
		String broadcasts;
		String status;
		Team homeTeam;
		Integer homeTeamScore;
		Team awayTeam;
		Integer awayTeamScore;
		
		String getTitleCompareVal() {
			return title;
		}
		String getDateTimeCompareVal() {
			return Format.date(Format.DATE_DB2_TIMESTAMP, dateTime);
		}
		
		void print() {
			System.out.println(title);
			System.out.println("  "+Format.date(Format.DATE_HUMAN, dateTime)+" on "+broadcasts+"       "+status);
			
			String winInd = "";
			if (status.equals("Final") && (awayTeamScore > homeTeamScore)) {
				winInd = "(W)";
			}
			String formattedTeamScore = Format.pad("    "+awayTeam.title+": "+awayTeamScore+"  "+winInd, 22, " ", true);
			System.out.println(formattedTeamScore+awayTeam.overallRecord+" ("+awayTeam.getOverallWinPct()+"%) overall, "+awayTeam.awayRecord+" ("+awayTeam.getAwayWinPct()+"%) on the road");
			
			winInd = "";
			if (status.equals("Final") && (awayTeamScore < homeTeamScore)) {
				winInd = "(W)";
			}
			formattedTeamScore = Format.pad("    "+homeTeam.title+": "+homeTeamScore+"  "+winInd, 22, " ", true);
			System.out.println(formattedTeamScore+homeTeam.overallRecord+" ("+homeTeam.getOverallWinPct()+"%) overall, "+homeTeam.homeRecord+" ("+homeTeam.getHomeWinPct()+"%) at home");
		}
	}
	
	class Team {
		String title;
		String overallRecord;
		String homeRecord;
		String awayRecord;
		
		String getOverallWinPct() {
			return getWinPct(overallRecord);
		}
		String getHomeWinPct() {
			return getWinPct(homeRecord);
		}
		String getAwayWinPct() {
			return getWinPct(awayRecord);
		}
		
		private String getWinPct(String record) {
			String[] wl = record.split("-");
			double w = Convert.toDouble(wl[0]);
			double l = Convert.toDouble(wl[1]);
			double total = w + l;
			double pct = (w / total) * 100;
			return Format.number(pct, 0);
		}
	}
}
