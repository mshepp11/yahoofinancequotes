package repos

import javax.inject.Inject

import play.api.libs.json.{JsObject, Json}
import scala.concurrent.{ExecutionContext, Future}
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.HttpClient
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.HttpResponse
import com.yahoofinance-api.YahooFinanceAPI

/* The purpose of this is to get the data from YahooFinanceAPI
*/

@Singleton
class StockQuotesController @Inject()
(ws: WSClient, configuration: Configuration, cc:ControllerComponents)
(implicit ec: ExecutionContext) extends AbstractController(cc) {

private val logger = Logger(this.getClass)

private val quotesURL = configuration.get[String](YahooFinanceAPI.QUOTES_BASE_URL)


case class Stock(symbol:String){

	final val stockQuotes:String

	def get(symbol:String): Action[AnyContent] = Action.async {
		logger.info(s"getting stock quotes data for $symbol")

		val futureStockQuotes: Future[Result] = for {
			quotes <- getStockQuotes(symbol)
			futureStockQuotes = loadData(quotes.json)
			stockQuotes <- Future.sequence(futureStockQuotes)
		} yield Ok(quotesJson(stockQuotes))
	}

	private def loadDataFromStock(json: JsValue): Seq[Future[WSResponse]] = {
		(json \ "symbol").as[Seq[Stock]] map (quotes -> getStockQuotes(quotes.text))
	}

	private def getStockQuotes(symbol:String) Future[WSResponse] = {
		logger.info(s"getStockQuotes: quotes = $quotes")
		ws.url(quotesURL.format(symbol)).get.withFilter { response =>
			response.status == OK
		}
	}

	private def quotesJson(quotes: Seq[WSResponse]):JsObject = {
		logger.info(s"quotesJson: quotes = $quotes")

		val response = Json.obj(
			"symbol" -> YahooFinanceAPI.getSymbol(symbol),
			"quote" -> YahooFinanceAPI.getQuote(quotes),
			"price" -> YahooFinanceAPI.getPrice(price)
		) 
		return response
		logger.info(s"response = " + response)
	}

	//updates values every 75 milliseconds
	def update: Source[StockUpdate, NotUsed] = {
    source
      .throttle(elements = 1, per = 75.millis, maximumBurst = 1, ThrottleMode.shaping)
      .map(sq => new StockUpdate(stockQuotes.symbol, stockQuotes.price))
  }

  override val toString: String = s"Stock($symbol)"
}
}

//Updates Stock Values
case class StockUpdate(symbol: StockSymbol, price: StockPrice)

object StockUpdate {
  import play.api.libs.json._ // Combinator syntax

  implicit val stockUpdateWrites: Writes[StockUpdate] = new Writes[StockUpdate] {
    override def writes(update: StockUpdate): JsValue = Json.obj(
      "type" -> "stockupdate",
      "symbol" -> update.symbol,
      "price" -> update.price
    )
  }
}