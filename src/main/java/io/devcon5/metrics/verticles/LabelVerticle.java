package io.devcon5.metrics.verticles;

import static io.devcon5.metrics.Constants.ADDRESS;
import static io.devcon5.metrics.Constants.MONGO;
import static io.devcon5.vertx.mongo.JsonFactory.toJsonArray;
import static java.util.stream.Collectors.toList;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import org.slf4j.Logger;

/**
 *
 */
public class LabelVerticle extends AbstractVerticle {

    private static final Logger LOG = getLogger(LabelVerticle.class);

    //this is list does not have to be thread safe as Verticles are single-threaded
    /**
     * local cache for all labels found
     */
    private List<String> queries = (new ArrayList<>());

    //timestamp of the last query update
    private AtomicLong lastQueriesUpdate = new AtomicLong();

    private MongoClient client;

    private String collectionName;
    private String address;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        final JsonObject mongoConfig = config().getJsonObject(MONGO);
        this.client = MongoClient.createShared(vertx, mongoConfig);
        this.collectionName = mongoConfig.getString("col_name");
        this.address = config().getString(ADDRESS, "/search");

        vertx.eventBus().consumer(this.address, this::searchLabels);
    }


    /**
     * Consumer for Event Bus Messages requesting labels
     *
     * @param msg
     */
    void searchLabels(Message<JsonObject> msg) {

        final JsonObject sq = msg.body();
        LOG.debug("{}\n{}", address, sq.encodePrettily());

        //extract the string query
        final String query = "select metric".equals(sq.getString("target")) ? "" : sq.getString("target");
        if (lastQueriesUpdate.get() + 60000 < System.currentTimeMillis() || this.queries.isEmpty()) {

            //retrieve list of unique names
            client.distinct(collectionName, "t.name", String.class.getName(), result -> {
                if (result.succeeded()) {
                    this.queries = result.result().stream().map(o -> (String) o).collect(toList());
                    this.lastQueriesUpdate.set(System.currentTimeMillis());
                } else {
                    LOG.error("Query for labels failed", result.cause());
                }
                msg.reply(this.queries.stream().filter(q -> q.startsWith(query)).collect(toJsonArray()));
            });
        } else {
            msg.reply(this.queries.stream().filter(q -> q.startsWith(query)).collect(toJsonArray()));
        }
    }
}
