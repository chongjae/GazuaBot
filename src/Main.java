import java.sql.Connection;

import java.sql.DriverManager;

import java.sql.ResultSet;

import java.sql.SQLException;

import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.HashMap;

import java.util.Set;

import java.util.logging.FileHandler;

import java.util.logging.Logger;

import java.util.logging.SimpleFormatter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import org.json.simple.parser.JSONParser;

import com.pengrad.telegrambot.TelegramBot;

import com.pengrad.telegrambot.model.request.ParseMode;

import com.pengrad.telegrambot.request.SendMessage;

public class Main {

	public static int minuteThreshold = 5 * 60;// 5 min to sec

	public static float rapidThreshold = 1.035f;

	public static float sellThreshold = 1.02f;

	public static int sellCount = 3;

	public static int logFrequency = 12;

	public static int buyCnt = 0;
	
	public static boolean isReallyBuy;

	public static TelegramBot bot = new TelegramBot("");

	public static Logger logger = Logger.getLogger("ChongCoinBot");

	public static Api_Client api = new Api_Client("",
			""); // connect key, secret key

	public static void main(String args[]) {

		HashMap<String, CoinInfo> coins = new HashMap<String, CoinInfo>();
		HashMap<String, String> rgParams = new HashMap<String, String>();
		FileHandler fh;
		isReallyBuy = "on".equals(args[0]);

		System.out.println(isReallyBuy);
		int logCnt = 0;

		HashMap<String, String> coinName = new HashMap<>();

		coinName.put("BTC", "비트코인");
		coinName.put("ETH", "이더리움");
		coinName.put("DASH", "대시");
		coinName.put("LTC", "라이트코인");
		coinName.put("ETC", "이더리움 클래식");
		coinName.put("XRP", "리플");
		coinName.put("BCH", "비트코인 캐시");
		coinName.put("XMR", "모네로");
		coinName.put("ZEC", "제트캐시");
		coinName.put("QTUM", "퀀텀");
		coinName.put("BTG", "비트코인 골드");
		coinName.put("EOS ", "이오스");

		try {

			fh = new FileHandler("./ChongCoinBot.log");

			logger.addHandler(fh);

			SimpleFormatter formatter = new SimpleFormatter();

			fh.setFormatter(formatter);

		} catch (Exception e) {
		}

		while (true) {

			try {
				String result = api.callApi("/public/ticker/ALL", rgParams);

				JSONParser parser = new JSONParser();

				JSONObject object = (JSONObject) parser.parse(result);

				object = (JSONObject) object.get("data");

				@SuppressWarnings("unchecked")

				Set<String> keys = object.keySet();

				HashMap<String, String> rapidSet = new HashMap<>();
				HashMap<String, String> sellSet = new HashMap<>();

				long date = 0;

				for (String key : keys) {

					if (key.equals("date")) {

						date = Long.parseLong(object.get(key).toString());

					} else {

						if (coins.containsKey(key)) {

							float curPrice = Float

									.parseFloat(((JSONObject) object.get(key)).get("buy_price").toString());

							CoinInfo coinInfo = coins.get(key);

							if (curPrice > coinInfo.maxPrice) {

								coinInfo.maxPrice = curPrice;

								coinInfo.maxDate = date;

							} else if (curPrice < coinInfo.minPrice) {

								coinInfo.minPrice = curPrice;

								coinInfo.minDate = date;

							}

							long diff = date - coinInfo.minDate;

							long diffSecond = (diff / 1000) % 60 + (diff / 1000) / 60 * 60; // second

							if (diffSecond <= minuteThreshold) {

								if (curPrice / coinInfo.minPrice >= rapidThreshold) {

									float rate = curPrice / coinInfo.minPrice;

									rapidSet.put(key, coinName.get(key) + "이 급등하였습니다. " + coinInfo.minPrice + " -> "
											+ curPrice + "("

											+ "(" + rate + ")");

									coinInfo.updateMin(date, curPrice);

									if (coinInfo.buyPrice == 0) {
										coinInfo.buyPrice = curPrice;
										if (isReallyBuy) {
											buyCoin(coinInfo);
										}
									}

								}

							} else {

								System.out.println("update min by over 5 min");

								coinInfo.updateMin(date, curPrice);

							}

							diff = date - coinInfo.maxDate;

							diffSecond = (diff / 1000) % 60 + (diff / 1000) / 60 * 60; // second

							if (diffSecond <= minuteThreshold) {

								if (coinInfo.maxPrice / curPrice >= sellThreshold && coinInfo.buyPrice != 0) {

									float rate = curPrice / coinInfo.buyPrice;

									if (rate <= 1.003f && coinInfo.sellCnt <= sellCount) {
										sellSet.put(key,
												coinName.get(key) + "를 " + coinInfo.buyPrice + "에 매수하여, " + curPrice
														+ "에 매도하였습니다. (" + rate + ")  -> " + coinInfo.sellCnt
														+ "차 손절 거부" + coinInfo.isReallyBuy);
										coinInfo.sellCnt = coinInfo.sellCnt + 1;
									} else {
										sellSet.put(key, coinName.get(key) + "를 " + coinInfo.buyPrice + "에 매수하여, "
												+ curPrice + "에 매도하였습니다. (" + rate + ") " + coinInfo.isReallyBuy);
										coinInfo.buyPrice = 0;
										coinInfo.sellCnt = 0;
										if (coinInfo.isReallyBuy) {
											sellCoin(coinInfo);
										}
									}

									coinInfo.updateMax(date, curPrice);

								}

							} else {

								System.out.println("update max by over 5 min");

								coinInfo.updateMax(date, curPrice);
								if (coinInfo.buyPrice != 0) {
									coinInfo.sellCnt = 0;
								}
							}

						} else {

							CoinInfo coinInfo = new CoinInfo(key, date,

									Float.valueOf(((JSONObject) object.get(key)).get("buy_price").toString()));

							coins.put(key, coinInfo);

						}

					}

				}

				if (rapidSet.size() > 0) {

					String ret = "";

					for (String rapidKey : rapidSet.keySet()) {

						ret += rapidSet.get(rapidKey) + "\n";

					}

					sendMsgToTelegram(ret, false);

				}
				if (sellSet.size() > 0) {
					String ret = "";

					for (String rapidKey : sellSet.keySet()) {

						ret += rapidKey + " : " + sellSet.get(rapidKey) + "\n";

					}

					sendMsgToTelegram(ret, true);
				}

				Thread.sleep(4 * 1000);

				logCnt++;

				if (logCnt == logFrequency) {

					logCnt = 0;

					logger.info("System is running....");

				}

			} catch (Exception e) {

				logger.info(e.getMessage());

			}

		}
	}

	public static void sendMsgToTelegram(String msg, boolean toMe) {

		logger.info("Send msg to Telegram : " + msg);

		Connection con = null;

		Statement stat = null;

		try {

			Class.forName("org.sqlite.JDBC");

			con = DriverManager.getConnection("jdbc:sqlite:user.db");

			stat = con.createStatement();

		} catch (Exception e) {

			e.printStackTrace();

		}

		try {

			ResultSet ret = stat.executeQuery("select * from user");

			while (ret.next()) {
				String userId = ret.getString("user");
				if (toMe) {
					if (!"196764827".equals(userId)) {
						continue;
					}
				}
				if("9999".equals(userId)) {
					isReallyBuy = false;
				}
				SendMessage request = new SendMessage(userId, msg).parseMode(ParseMode.HTML);
				bot.execute(request);
			}

		} catch (SQLException e) {

			e.printStackTrace();

		}

	}

	public static void buyCoin(CoinInfo coin) {
		HashMap<String, String> rgParams = new HashMap<String, String>();
		String result = "";
		try {
			DecimalFormat format = new DecimalFormat(".####");
			float krw = getBalance("KRW");
			if (buyCnt == 0) {
				krw *= 0.3;
			} else if (buyCnt == 1) {
				krw *= 0.4;
			} else if (buyCnt == 2) {
				krw *= 0.7;
			}
			rgParams.put("units", String.valueOf(format.format(krw / (coin.buyPrice * 1.1))));
			rgParams.put("currency", coin.key);
			result = api.callApi("/trade/market_buy", rgParams);
			JSONParser parser = new JSONParser();
			JSONArray array = (JSONArray) ((JSONObject) parser.parse(result)).get("data");

			for (int i = 0; i < array.size(); i++) {
				JSONObject obj = (JSONObject) array.get(i);
				coin.buyPrice = Float.valueOf(obj.get("price").toString());
			}
			if (coin.isReallyBuy) {
				buyCnt++;				
				coin.isReallyBuy = true;
			}
		} catch (Exception e) {
			logger.info(rgParams.toString());
			sendMsgToTelegram("Buy fail..." + result, true);
		}
		logger.info(result);
	}

	public static void sellCoin(CoinInfo coin) {
		HashMap<String, String> rgParams = new HashMap<String, String>();
		try {
			String unit = String.valueOf(getBalance(coin.key));
			try {
				unit = unit.substring(0, unit.indexOf(".") + 5);
			} catch (Exception e) {
				try {
					unit = unit.substring(0, unit.indexOf(".") + 4);
				} catch (Exception e1) {
					try {
						unit = unit.substring(0, unit.indexOf(".") + 3);
					}catch (Exception e2) {
					}
				}
			}
			rgParams.put("units", "" + unit);
			rgParams.put("currency", coin.key);
			String result = api.callApi("/trade/market_sell", rgParams);
			JSONParser parser = new JSONParser();
			if ("0000".equals(((JSONObject) parser.parse(result)).get("status").toString())) {
				coin.isReallyBuy = false;
				buyCnt--;
			}
			logger.info(result);
		} catch (Exception e) {
			logger.info(rgParams.toString());
			logger.info(e.getMessage());
		}
	}

	@SuppressWarnings("finally")
	public static float getBalance(String currency) {
		float money = 0;
		try {
			HashMap<String, String> rgParams = new HashMap<String, String>();
			rgParams.put("currency", "ALL");
			String result = api.callApi("/info/balance", rgParams);

			JSONParser parser = new JSONParser();

			JSONObject object = (JSONObject) ((JSONObject) parser.parse(result)).get("data");

			money = Float.valueOf(object.get("available_" + currency.toLowerCase()).toString());
		} catch (Exception e) {
			logger.info(e.getMessage());
		} finally {
			return money;
		}
	}

	public static class CoinInfo {

		public String key;

		public long minDate;

		public long maxDate;

		public float minPrice;

		public float maxPrice;

		public float buyPrice;

		public int sellCnt;

		public boolean isReallyBuy = false;

		public CoinInfo(String key, long date, float price) {

			this.key = key;

			minDate = date;

			maxDate = date;

			minPrice = price;

			maxPrice = price;

		}

		public void updateMax(long date, float price) {

			maxDate = date;

			maxPrice = price;

		}

		public void updateMin(long date, float price) {

			minDate = date;

			minPrice = price;

		}

	}

}