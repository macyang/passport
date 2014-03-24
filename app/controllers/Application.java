package controllers;

import play.Logger;
import play.libs.F.*;
import play.libs.WS;
import play.libs.Json;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

public class Application extends Controller {
  
  public static Result index() {
    return ok("hello world");
  }

  public static Promise<Result> async() {

    Promise<WS.Response> checkinListPromise = WS.url("http://lotus-stresstest.appspot.com/guide/passport")
      .setQueryParameter("imei", "99000293000988")
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
	    String l = jn.findPath("link").textValue();
	    String[] la = l.split("\\?");
	    String[] lb = la[1].split("=");
	    promiseList.add(WS.url(la[0]).setQueryParameter(lb[0], lb[1]).get());
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
		  Iterator<JsonNode> events = resp.asJson().findPath("events_").elements();
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
