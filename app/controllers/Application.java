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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

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
  public static Promise<Result> checkin(String id) {

    Promise<WS.Response> checkinListPromise = WS.url("http://dspipeline.appspot.com/guide/passport")
      // .setQueryParameter("imei", "99000293000988")
      .setQueryParameter("serialNumber", id)
      .setQueryParameter("limit", "10")
      .get();

    return checkinListPromise.flatMap(
      new Function<WS.Response, Promise<Result>>() {
	@Override
	public Promise<Result> apply(WS.Response resp) {
	  List<Promise<WS.Response>> promiseList = new ArrayList<Promise<WS.Response>>();
	  Iterator<JsonNode> checkins = resp.asJson().elements();

	  while (checkins.hasNext()) {
	    JsonNode jn = checkins.next();
/*
	    String l = jn.findPath("link").textValue();
	    String[] la = l.split("\\?");
	    String[] lb = la[1].split("=");
	    promiseList.add(WS.url(la[0]).setQueryParameter(lb[0], lb[1]).get());
*/
	    String l = jn.findPath("passportId").textValue();
	    promiseList.add(WS.url("http://dspipeline.appspot.com/processor/processcheckin").setQueryParameter("passport-id", l).get());
	  }
	  Promise<WS.Response>[] promiseArray = promiseList.toArray(new Promise[promiseList.size()]);
	  Promise<List<WS.Response>> promiseSequence = Promise.sequence(promiseArray);

	  return promiseSequence.map(
	    new Function<List<WS.Response>, Result>() {
	      @Override
	      public Result apply(List<WS.Response> resps) {
		String s = "";
		ArrayNode an = Json.newObject().arrayNode();
		for (WS.Response resp : resps) {
		  // Iterator<JsonNode> events = resp.asJson().findPath("events_").elements();
		  Iterator<JsonNode> events = resp.asJson().findPath("parsedRecords").elements();
		  while (events.hasNext()) {
		    an.add(events.next());
		  }
		}
		return ok(an.toString());
	      }
	    }
	  );
	}
      }
    );
  }
}
