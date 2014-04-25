package controllers;

import play.Logger;
import play.libs.F.*;
import play.libs.WS;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import play.mvc.WebSocket;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import models.*;

public class Application extends Controller {

  public static WebSocket<String> index() {
    return new WebSocket<String>() {
	
      // Called when the Websocket Handshake is done.
      public void onReady(WebSocket.In<String> in, final WebSocket.Out<String> out) {
	
	// For each event received on the socket,
	in.onMessage(new Callback<String>() {
	   public void invoke(String event) {
	       
	     // Log events to the console
	     Logger.debug(event);  
	     out.write("received " + event);
	   } 
	});
	
	// When the socket is closed.
	in.onClose(new Callback0() {
	   public void invoke() {
	       
	     Logger.debug("Disconnected");
	       
	   }
	});
	
	// Send a single 'Hello!' message
	out.write("Hello!");
	
      }
      
    };
  }
  
  public static WebSocket<JsonNode> analyze(final String id) {
    return new WebSocket<JsonNode>() {
	
      // Called when the Websocket Handshake is done.
      public void onReady(WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) {
	try { 
	  Tricorder.analyze(id, in, out);
	} catch (Exception ex) {
	  ex.printStackTrace();
	}
      }
    };
  }

  /*
   * Example: http://localhost:9000/checkin/99000293000988
   */
  public static Promise<Result> checkin(String id, final String since, final String filter) {

    Promise<WS.Response> checkinListPromise = WS.url("http://dspipeline.appspot.com/guide/passport")
      // .setQueryParameter("imei", "99000293000988")
      // .setQueryParameter("imei", "990002025609437")
      .setQueryParameter("serialnumber", id)
      .setQueryParameter("since", since)
      .setQueryParameter("limit", "500")
      .setQueryParameter("tagfilter", filter)
      .get();
Logger.debug("xxx serialnumber = " + id);

    return checkinListPromise.flatMap(
      new Function<WS.Response, Promise<Result>>() {
	@Override
	public Promise<Result> apply(WS.Response resp) {
	  List<Promise<WS.Response>> promiseList = new ArrayList<Promise<WS.Response>>();
	  Iterator<JsonNode> checkins = resp.asJson().elements();

	  while (checkins.hasNext()) {
	    JsonNode jn = checkins.next();
	    String l = jn.findPath("pid").textValue();
Logger.debug("xxx passportid = " + l);
	    promiseList.add(WS.url("http://dspipeline.appspot.com/processor/processcheckin")
	      .setQueryParameter("passportid", l)
	      .setQueryParameter("tagfilter", filter)
	      .setQueryParameter("column", "ltime,devicetime,tag,idtag,segid,event")
	      .get());
	  }
	  Promise<WS.Response>[] promiseArray = promiseList.toArray(new Promise[promiseList.size()]);
	  Promise<List<WS.Response>> promiseSequence = Promise.sequence(promiseArray);

	  return promiseSequence.map(
	    new Function<List<WS.Response>, Result>() {
	      @Override
	      public Result apply(List<WS.Response> resps) {
		String s = "";
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode an = Json.newObject().arrayNode();
		for (WS.Response resp : resps) {
		  // Iterator<JsonNode> events = resp.asJson().findPath("events_").elements();
		  Iterator<JsonNode> events = resp.asJson().findPath("parsedRecords").elements();
/*
http://107.178.223.122:9000/checkin/990002025609437?filter=MOT_DEVICE_STATS:AppUsage&since=2014-04-15&limit=300
NOTE: this is not general purpose code, only work with the AppUsage data for the demo
A event looks like this,
{
event: "{"ID":"appdata","package":"com.google.android.youtube","app_duration_ms":"6137442","launch_count":"14","user1_or_pre0_install":"0"}",
tag: "MOT_DEVICE_STATS",
devicetime: "2014-04-24 05:00:07",
idtag: "AppUsage",
segid: "appdata",
ltime: "2014-04-24 05:04:51"
}
 */
		  while (events.hasNext()) {
		    try {
		    JsonNode jn = events.next();
		    if ("appdata".equals(jn.get("segid").textValue())) {
		      String date = jn.get("devicetime").textValue().substring(0, 10);
		      ObjectNode event = mapper.readValue(jn.get("event").textValue(), ObjectNode.class);
		      event.remove("ID");
		      event.put("date", date);
		      an.add(event);
		    }
		    } catch (Exception e) {
		      Logger.debug(e.getMessage());
		    }
		  }
		}
		response().setHeader("Access-Control-Allow-Origin", "*");
		return ok(an.toString());
	      }
	    }
	  );
	}
      }
    );
  }

  public static Result checkPreFlight() {
      response().setHeader("Access-Control-Allow-Origin", "*");       // Need to add the correct domain in here!!
      // response().setHeader("Access-Control-Allow-Methods", "POST");   // Only allow POST
      response().setHeader("Access-Control-Max-Age", "300");          // Cache response for 5 minutes
      response().setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");         // Ensure this header is also allowed!  
      return ok();
  }
}
