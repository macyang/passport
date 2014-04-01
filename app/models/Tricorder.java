package models;

import play.mvc.*;
import play.libs.*;
import play.libs.F.*;

import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import akka.actor.*;
import static akka.pattern.Patterns.ask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.*;

import static java.util.concurrent.TimeUnit.*;

public class Tricorder extends UntypedActor {

  public static void analyze(final String id, WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) throws Exception{
    ObjectNode jn = Json.newObject();
    out.write(jn.put("message", "Tricorder.analyze(" + id + ")"));
  }

  public void onReceive(Object message) throws Exception {
  }
}
