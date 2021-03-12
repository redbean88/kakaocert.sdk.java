package com.kakaocert.api;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;
import com.kakaocert.api.cms.RequestCMS;
import com.kakaocert.api.cms.ResultCMS;
import com.kakaocert.api.esign.RequestESign;
import com.kakaocert.api.esign.ResultESign;
import com.kakaocert.api.verifyauth.RequestVerifyAuth;
import com.kakaocert.api.verifyauth.ResultVerifyAuth;

import kr.co.linkhub.auth.LinkhubException;
import kr.co.linkhub.auth.Token;
import kr.co.linkhub.auth.TokenBuilder;

public class KakaocertServiceImp implements KakaocertService{

	private static final String ServiceID = "KAKAOCERT";
	private static final String Auth_GA_URL= "https://ga-auth.linkhub.co.kr";
	private static final String ServiceURL_REAL = "https://kakaocert-api.linkhub.co.kr";
	private static final String ServiceURL_GA_REAL = "https://ga-kakaocert-api.linkhub.co.kr";
	private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
	private final String APIVersion = "1.0";
	private String ServiceURL = null;
	private String AuthURL = null;
	
	private TokenBuilder tokenBuilder;
	
	private boolean isIPRestrictOnOff;
	private boolean useStaticIP;
	private String _linkID;
	private String _secretKey;
	private Gson _gsonParser = new Gson();

	private Map<String, Token> tokenTable = new HashMap<String, Token>();
	
	public KakaocertServiceImp() {
		isIPRestrictOnOff = true;
		useStaticIP = false;
	}
	
	public void setIPRestrictOnOff(boolean isIPRestrictOnOff) {
		this.isIPRestrictOnOff = isIPRestrictOnOff;
	}

	public void setUseStaticIP(boolean useStaticIP) {
		this.useStaticIP = useStaticIP;
	}
	
	public boolean isUseStaticIP() {
		return useStaticIP;
	}
	
	public String getServiceURL() {
		
		if(ServiceURL != null) return ServiceURL;
		
		if(useStaticIP) {
			return ServiceURL_GA_REAL;
		}
		return ServiceURL_REAL;
	}

	public void setServiceURL(String serviceURL) {
		ServiceURL = serviceURL;
	}

	public String getAuthURL() {
		return AuthURL;
	}

	public void setAuthURL(String authURL) {
		AuthURL = authURL;
	}

	public String getLinkID() {
		return _linkID;
	}

	public void setLinkID(String linkID) {
		this._linkID = linkID;
	}

	public String getSecretKey() {
		return _secretKey;
	}

	public void setSecretKey(String secretKey) {
		this._secretKey = secretKey;
	}
	
	/**
	 * <pre>토큰을 획득</pre>
	 * <pre>1. 기존 토큰이 없을 경우, 링크아이디, 비밀키, 서비스아이디, 스코프를 정하여 생성(빌터)</pre>
	 * <pre>2. ServiceURL을 설정.</pre>
	 * <pre>		1. KakaoCert 서비스 생성시 설정한 AuthURL이 있는 경우, AuthURL을 ServiceURL로 사용</pre>
	 * <pre>		2. KakaoCert 서비스 생성시 설정한 AuthURL이 없고, useStaticIP값이 Ture인 경우, Auth_GA_URL을 ServiceURL로 사용(확인필요)</pre>
	 * <pre>		3. KakaoCert 서비스 생성시 설정한 AuthURL이 없고, useStaticIP값이 False인 경우(기본값), ServiceURL을 설정안함(확인필요)</pre>
	 * <pre>		4. 스코프 값을 추가(310,320,330) </pre>
	 * @return tokenBuilder
	 */
	private TokenBuilder getTokenbuilder() {
		if (this.tokenBuilder == null) {
			tokenBuilder = TokenBuilder
					.newInstance(getLinkID(), getSecretKey())
					.ServiceID(ServiceID)
					.addScope("member");
			
			if(AuthURL != null) {
				tokenBuilder.setServiceURL(AuthURL);
			} else {
				// AuthURL 이 null이고, useStaticIP 가 True인 경우. GA-AUTH 호출.
				if(useStaticIP) {
					tokenBuilder.setServiceURL(Auth_GA_URL);
				}
			}
			
			tokenBuilder.addScope("310");	
			tokenBuilder.addScope("320");	
			tokenBuilder.addScope("330");	
		}

		return tokenBuilder;
	}
	
	/**
	 * <pre>세션으로 관리하는 토큰을 획득</pre>
	 * <pre>1. 이용기관 코드(clinentCode)를 확인후 없으면, Exception을 전달 </pre>
	 * <pre>2. tokenTable에 해당 이용기관의 token이 존재시, token에 할당</pre>
	 * <pre>3. token이 존재하면, token 만료기한과 API서버 시간(UTCTime)을 비교하여, API서버 시간(UTCTime)이 만료기한보다 이전이면 true, 아니면 false를 반환</pre>
	 * <pre>4. token이 만료되었다면, tokenTable에 해당 이용기관의 token이 존재시, 해당 이용기관의 token을 제거</pre>
	 * <pre>	1. 인증토큰 발급 IP 제한 값(isIPRestrictOnOff)이 True일 경우, 이용기관코드(ClientCode - 빌더내부에서는 AccessID로 사용)과 이용가능 IP를 할당하여 토큰을 생성</pre>
	 * <pre>	2. 인증토큰 발급 IP 제한 값(isIPRestrictOnOff)이 False일 경우, 이용기관코드(ClientCode - 빌더내부에서는 AccessID로 사용)과 이용가능 IP를 와일드카드(*)로 할당하여 토큰을 생성</pre>
	 * <pre>	3. 생성된 토큰을 tokenTable에 삽입</pre>
	 * 
	 * @param ClientCode
	 * @param ForwardIP
	 * @return token.session_token
	 * @throws KakaocertException
	 */
	private String getSessionToken(String ClientCode, String ForwardIP)
			throws KakaocertException {

		if (ClientCode == null || ClientCode.isEmpty())
			throw new KakaocertException(-99999999, "이용기관 코드가 입력되지 않았습니다.");

		Token token = null;
		Date UTCTime = null;

		//토큰 재사용
		if (tokenTable.containsKey(ClientCode))
			token = tokenTable.get(ClientCode);

		//만료 여부 확인값
		boolean expired = true;
		
		if (token != null) {
			
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			format.setTimeZone(TimeZone.getTimeZone("UTC"));
			SimpleDateFormat subFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			subFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			
			try {
				Date expiration = format.parse(token.getExpiration()); //만료기한
				UTCTime = subFormat.parse(getTokenbuilder().getTime());
				expired = expiration.before(UTCTime);	//만료기한이  API서버 시간(UTCTime)보다 이전이면 true, 아니면 false를 반환한다.
				
			} catch (LinkhubException le){
				throw new KakaocertException(le);
			} catch (ParseException e){
			}
		}

		if (expired) {
			if (tokenTable.containsKey(ClientCode))
				tokenTable.remove(ClientCode);

			try {
				
				if (isIPRestrictOnOff) {
					token = getTokenbuilder().build(ClientCode, ForwardIP);
				} else {
					token = getTokenbuilder().build(ClientCode, "*");
				}
				
				tokenTable.put(ClientCode, token);
			} catch (LinkhubException le) {
				throw new KakaocertException(le);
			}
		}

		return token.getSession_token();
	}

	
	
	
	protected class ReceiptResponse {
        public String receiptId;
    }
	
	
	
	/**
	 * Convert Object to Json String.
	 * 
	 * @param Graph
	 * @return jsonString
	 */
	protected String toJsonString(Object Graph) {
		return _gsonParser.toJson(Graph);
	}

	/**
	 * Convert JsonString to Object of Clazz
	 * 
	 * @param json
	 * @param clazz
	 * @return Object of Clazz
	 */
	protected <T> T fromJsonString(String json, Class<T> clazz) {
		return _gsonParser.fromJson(json, clazz);
	}

	/**
	 * 
	 * @param url
	 * @param CorpNum
	 * @param PostData
	 * @param UserID
	 * @param clazz
	 * @return returned object
	 * @throws KakaocertException
	 */
	protected <T> T httppost(String url, String CorpNum, String PostData,String UserID, Class<T> clazz) throws KakaocertException {
		/** 
		 * @NOTE Action이 왜 널인가?
		 * httppost(String url, String CorpNum, String PostData, String UserID, String Action, Class<T> clazz)
		 * */
		return httppost(url, CorpNum, PostData, UserID, null, clazz);	
	}
		
	/**
	 * 
	 * @param url
	 * @param CorpNum
	 * @param PostData
	 * @param UserID
	 * @param Action
	 * @param clazz
	 * @return returned object
	 * @throws KakaocertException
	 */
	protected <T> T httppost(String url, String CorpNum, String PostData, String UserID, String Action, Class<T> clazz)
			throws KakaocertException {
		/**
		 * 두번 wraping할 이유가 있었나?
		 * httppost(String url, String CorpNum, String PostData, String UserID, String Action, Class<T> clazz, String ContentType)
		 * */
		return httppost(url, CorpNum, PostData, UserID, Action, clazz, null);	
	}	

	/**
	 * 단방향 암호화
	 * @param input
	 * @return
	 */
	private static String md5Base64(byte[] input) {
    	MessageDigest md;
    	byte[] btResult = null;
		try {
			md = MessageDigest.getInstance("MD5");
			btResult = md.digest(input);
		} catch (NoSuchAlgorithmException e) {	}
    	
    	return base64Encode(btResult);
    }
    
    private static byte[] base64Decode(String input) {
    	return DatatypeConverter.parseBase64Binary(input);
    }
    
    private static String base64Encode(byte[] input) {
    	return DatatypeConverter.printBase64Binary(input);
    }
    
    private static byte[] HMacSha1(byte[] key, byte[] input) throws KakaocertException {
    	try
    	{
			SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
			Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
			mac.init(signingKey);
			return mac.doFinal(input);
    	}
    	catch(Exception e) 
    	{
    		throw new KakaocertException(-99999999, "Fail to Calculate HMAC-SHA1, Please check your SecretKey.",e);
    	}
	}
    
	/**
	 * <pre>API 서버에 url에 해당하는 정보를 요청(post)</pre>
	 * <pre>1. http 커넥션 생성</pre>
	 * <pre>2. 이용기관 코드를 키로하는 인증용 토큰을 가져와 Authorization에 할당</pre>
	 * <pre>3. 링크허브 연동을 위한 값을 할당</pre>
	 * <pre>	1. x-lh-date : API서버에서 해당 값을 기준으로 유효기간을 확인</pre>
	 * <pre>	2. x-lh-version </pre>
	 * <pre>	3. contentType 설정( 기본값 : application/json; charset=utf8 )</pre>
	 * <pre>	4. 압축방식 설정 ( "Accept-Encoding" , "gzip")</pre>
	 * <pre>	5. RequestMethod를 post로 설정</pre>
	 * <pre> 	6. 캐시저장값 미사용처리(setUseCaches(false))</pre>
	 * <pre> 	7. Post방식 사용을 위한 출력 스트림 사용 가능하도록 지정 (setDoOutput(true))</pre>
	 * <pre>4. PostData에 값이 있을 경우</pre>
	 * <pre>	1. json으로 파싱된 request Object(이하 요청값)의 길이 설정("Content-Length")</pre>
	 * <pre>	2. 요청값을 단방향 암호화하여, http메소드,암호화된 요청값 , 생성시간 , APIversion값을 이용하여 본문 생성(singTarget)</pre>
	 * <pre>	3. 유효성 검증을 위해, hmacsha1(키는 시크릿키며, 데이터는 암호화된 요청값)을 이용하여 서명(Signature)을 생성하여 설정("x-kc-auth")</pre>
	 * <pre>5. 응답을 회신하여 json 데이터를 두번째 arguemnet 타입으로 파싱 처리</pre>
	 * 
	 * @param url
	 * @param CorpNum
	 * @param PostData
	 * @param UserID
	 * @param Action
	 * @param clazz
	 * @param ContentType
	 * @return
	 * @throws KakaocertException
	 */
	protected <T> T httppost(String url, String CorpNum, String PostData, String UserID, String Action, Class<T> clazz, String ContentType)
			throws KakaocertException {
		//http커넥션 생성
		HttpURLConnection httpURLConnection = makeHttpUrlConnection(url);

		//현재 시간 획득
		String date = nowTime();

		
		if (CorpNum != null && CorpNum.isEmpty() == false) {
			httpURLConnection.setRequestProperty("Authorization", "Bearer "+ getSessionToken(CorpNum, null));
		}
		
		/**
		 * 링크허브 연동을 위한 인증 기본값 설정
		 */

		httpURLConnection.setRequestProperty("x-lh-date".toLowerCase(),date);	//API 서버에서 해당 값을 기준으료 유효기간을 확인한다.
		httpURLConnection.setRequestProperty("x-lh-version".toLowerCase(),APIVersion);

		//contentType 설정
		setupContentType(ContentType, httpURLConnection);
		
		httpURLConnection.setRequestProperty("Accept-Encoding",	"gzip");

		try {
			httpURLConnection.setRequestMethod("POST");
		} catch (ProtocolException e1) {
			/**
			 * ne 는 makeHttpUrlConnection서 처리하고 있는데, 추가 처리가 왜 필요한지 모르겠
			 */
		}

		httpURLConnection.setUseCaches(false);	// 캐시저장 값 미사용
		httpURLConnection.setDoOutput(true);	// Post방식 사용을 위한 출력 스트림 사용 가능하도록 지정

		if ((PostData == null || PostData.isEmpty()) == false) {

			byte[] btPostData = PostData.getBytes(Charset.forName("UTF-8"));

			httpURLConnection.setRequestProperty("Content-Length",
					String.valueOf(btPostData.length));
			
			String signTarget = "POST\n";
			signTarget += md5Base64(btPostData)  + "\n";
			signTarget += date + "\n";
			signTarget += APIVersion + "\n";
			//시크릿 키를 암호화
			//base64 디코딩 > Hmacsha1 암호화(단반향) > base64 인코딩 
			byte[] base64Decode = base64Decode(getSecretKey());
			byte[] hMacSha1 = HMacSha1(base64Decode, signTarget.getBytes(Charset.forName("UTF-8")));
			String Signature = base64Encode(hMacSha1);
			
			httpURLConnection.setRequestProperty("x-kc-auth", getLinkID() + " " + Signature);
			
			DataOutputStream output = null;
			
			try {
				output = new DataOutputStream(httpURLConnection.getOutputStream());
				output.write(btPostData);
				output.flush();
			} catch (Exception e) {
				throw new KakaocertException(-99999999,
						"Fail to POST data to Server.", e);
			} finally {
				try {
					if (output != null) {
						output.close();
					}
				} catch (IOException e1) {
					throw new KakaocertException(-99999999, 
							"Kakaocert httppost func DataOutputStream close() Exception", e1);
				}
			}
		}
		
		String Result = parseResponse(httpURLConnection);
		return fromJsonString(Result, clazz);		
	}

	private void setupContentType(String ContentType, HttpURLConnection httpURLConnection) {
		if (ContentType != null && ContentType.isEmpty() == false) {
			httpURLConnection.setRequestProperty("Content-Type", ContentType);			
		} else {
			httpURLConnection.setRequestProperty("Content-Type",
					"application/json; charset=utf8");			
		}
	}

	private String nowTime() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		String date = format.format(new Date());
		return date;
	}

	private HttpURLConnection makeHttpUrlConnection(String url) throws KakaocertException {
		HttpURLConnection httpURLConnection;
		try {
			URL uri = new URL(getServiceURL() + url);
			httpURLConnection = (HttpURLConnection) uri.openConnection();
		} catch (Exception e) {
			throw new KakaocertException(-99999999, "Kakaocert API 서버 접속 실패", e);
		}
		return httpURLConnection;
	}	
	

	/**
	 *<pre> API 서버에 url에 해당하는 정보를 요청(get)</pre>
	 *<pre> 1. http 커넥션 생성</pre>
	 *<pre> 2. 이용기관 코드를 키로하는 인증용 토큰을 가져와 Authorization에 할당</pre>
	 *<pre> 3. 링크허브 연동을 위한 값을 할당</pre>
	 *<pre> 	1. x-lh-version </pre>
	 *<pre> 	2. 접수아이디 설정( "x-pb-userid" , "receiptID")</pre>
	 *<pre> 	3. 압축방식 설정 ( "Accept-Encoding" , "gzip")</pre>
	 *<pre> 4. 응답을 회신하여 json 데이터를 두번째 arguemnet 타입으로 파싱 처리</pre>
	 * @param url
	 * @param CorpNum
	 * @param UserID
	 * @param clazz
	 * @return returned object
	 * @throws KakaocertException
	 */
	protected <T> T httpget(String url, String CorpNum, String UserID,
			Class<T> clazz) throws KakaocertException {
		HttpURLConnection httpURLConnection = makeHttpUrlConnection(url);

		if (CorpNum != null && CorpNum.isEmpty() == false) {
			httpURLConnection.setRequestProperty("Authorization", "Bearer "
					+ getSessionToken(CorpNum, null));
		}

		httpURLConnection.setRequestProperty("x-pb-version".toLowerCase(),
				APIVersion);

		if (UserID != null && UserID.isEmpty() == false) {
			httpURLConnection.setRequestProperty("x-pb-userid", UserID);
		}
		
		httpURLConnection.setRequestProperty("Accept-Encoding",	"gzip");
		
		String Result = parseResponse(httpURLConnection);
		
		return fromJsonString(Result, clazz);
	}


	private class ErrorResponse {

		private long code;
		private String message;

		public long getCode() {
			return code;
		}

		public String getMessage() {
			return message;
		}

	}

	protected class UnitCostResponse {

		public float unitCost;

	}

	protected class UploadFile {
		public UploadFile() {
		}

		public String fieldName;
		public String fileName;
		public InputStream fileData;
	}

	protected class URLResponse {
		public String url;
	}
	
	/**
	 * 압축되지 않은 스트림을 문자열로 변환
	 * @param input
	 * @return
	 * @throws KakaocertException
	 */
	private static String fromStream(InputStream input) throws KakaocertException {
		InputStreamReader is = null;
		BufferedReader br = null;
		StringBuilder sb = null;
		
		try{
			is = new InputStreamReader(input, Charset.forName("UTF-8"));
			br = new BufferedReader(is);
			sb = new StringBuilder();
	
			String read = br.readLine();

			while (read != null) {
				sb.append(read);
				read = br.readLine();
			}
			
		} catch (IOException e){
			throw new KakaocertException(-99999999, 
					"Kakaocert fromStream func Exception", e);
		} finally {
			try {
				if (br != null) br.close();
				if (is != null) is.close();
			} catch (IOException e) { 
				throw new KakaocertException(-99999999,
					"Kakaocert fromStream func finally close Exception", e);
			}
		}
		
		return sb.toString();
	}
	/**
	 * Gzip으로 압축된 스트림은 압축해제후 문자열로 변환
	 * @param input
	 * @return
	 * @throws KakaocertException
	 */
	private static String fromGzipStream(InputStream input) throws KakaocertException {
		GZIPInputStream zipReader = null;
		InputStreamReader is = null;		
		BufferedReader br = null;
		StringBuilder sb = null;
		
		try {
			zipReader = new GZIPInputStream(input);
			is = new InputStreamReader(zipReader, "UTF-8");
			br = new BufferedReader(is);
			sb = new StringBuilder();
	
			String read = br.readLine();
	
			while (read != null) {
				sb.append(read);
				read = br.readLine();
			}
		} catch (IOException e) {
			throw new KakaocertException(-99999999, 
					"Kakaocert fromGzipStream func Exception", e);
		} finally {
			try {
				if (br != null) br.close();
				if (is != null) is.close();
				if (zipReader != null) zipReader.close();
			} catch (IOException e) {
				throw new KakaocertException(-99999999,
					"Kakaocert fromGzipStream func finally close Exception", e);
			}
		}
		
		return sb.toString();
	}
		
	/**
	 * 응답 데이터를 문자열로 반환
	 * @param httpURLConnection
	 * @return
	 * @throws KakaocertException
	 */
	private String parseResponse(HttpURLConnection httpURLConnection) throws KakaocertException {
		
		String result = "";
		InputStream input = null;
		KakaocertException exception = null;
		
		try {
			input = httpURLConnection.getInputStream();
			
			if (null != httpURLConnection.getContentEncoding() && httpURLConnection.getContentEncoding().equals("gzip")) {
				result = fromGzipStream(input);
			} else {
				result = fromStream(input);
			}
		} catch (IOException e) {
			InputStream errorIs = null;
			ErrorResponse error = null;
			
			try {
				errorIs = httpURLConnection.getErrorStream(); //에러가 발생시, 에러내용을 반환하는 스트림
				result = fromStream(errorIs);
				error = fromJsonString(result, ErrorResponse.class);
			} catch (Exception ignored) { 
				
			} finally {
				try {
					if (errorIs != null) {
						errorIs.close();
					}
				} catch (IOException e1) {
					throw new KakaocertException(-99999999, 
							"Kakaocert parseResponse func InputStream close() Exception", e1);
				}
			}
			
			if (error == null) {
				exception = new KakaocertException(-99999999,
						"Fail to receive data from Server.", e);
			} else {
				exception = new KakaocertException(error.getCode(), error.getMessage());
			}
		} finally {
			try {
				if (input != null) {
					input.close();
				}
			} catch (IOException e2) {
				throw new KakaocertException(-99999999, 
						"Kakaocert parseResponse func InputStream close() Exception", e2);
			}
		}
		
		if (exception != null)
			throw exception;
		
		return result;
	}

	@Override
	public String requestESign(String ClientCode, RequestESign esignRequest) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == esignRequest) throw new KakaocertException(-99999999, "전자서명 요청정보가 입력되지 않았습니다.");
		
		String PostData = toJsonString(esignRequest);
		
		esignRequest.setAppUseYN(false);
		
		ReceiptResponse response = httppost("/SignToken/Request", ClientCode, PostData, null, ReceiptResponse.class);
		
		return response.receiptId;
	}
	
	@Override
	public ResponseESign requestESign(String ClientCode, RequestESign esignRequest, boolean appUseYN) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == esignRequest) throw new KakaocertException(-99999999, "전자서명 요청정보가 입력되지 않았습니다.");
		
		esignRequest.setAppUseYN(appUseYN);
		
		String PostData = toJsonString(esignRequest);
		ResponseESign response = httppost("/SignToken/Request", ClientCode, PostData, null, ResponseESign.class);
		
		return response;
	}
	
	@Override
	public String requestVerifyAuth(String ClientCode, RequestVerifyAuth verifyAuthRequest) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == verifyAuthRequest) throw new KakaocertException(-99999999, "본인인증 요청정보가 입력되지 않았습니다.");
		
		String PostData = toJsonString(verifyAuthRequest);
		
		//API서버에 해당 컨트롤러 호출
		ReceiptResponse response = httppost("/SignIdentity/Request", ClientCode, PostData, null, ReceiptResponse.class);
		
		return response.receiptId;
	}
	
	@Override
	public String requestCMS(String ClientCode, RequestCMS cmsRequest) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == cmsRequest) throw new KakaocertException(-99999999, "자동이체 출금동의 요청정보가 입력되지 않았습니다.");
		
		String PostData = toJsonString(cmsRequest);
		
		ReceiptResponse response = httppost("/SignDirectDebit/Request", ClientCode, PostData, null, ReceiptResponse.class);
		
		return response.receiptId;
	}
	
	
	@Override
	public ResultESign getESignState(String ClientCode, String receiptID) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignToken/Status/" + receiptID, ClientCode, null,
				ResultESign.class);
	}
	
	@Override
	public ResultVerifyAuth getVerifyAuthState(String ClientCode, String receiptID) throws KakaocertException {

		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignIdentity/Status/" + receiptID, ClientCode, null,
				ResultVerifyAuth.class);
	}


	@Override
	public ResultCMS getCMSState(String ClientCode, String receiptID) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignDirectDebit/Status/" + receiptID, ClientCode, null,
				ResultCMS.class);
	}

	@Override
	public VerifyResult verifyESign(String ClientCode, String receiptID) throws KakaocertException {
		
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignToken/Verify/" + receiptID, ClientCode, null,
				VerifyResult.class);
	}

	@Override
	public VerifyResult verifyESign(String ClientCode, String receiptID, String signature) throws KakaocertException {
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignToken/Verify/" + receiptID+"/"+signature, ClientCode, null,
				VerifyResult.class);
	}

	@Override
	public VerifyResult verifyAuth(String ClientCode, String receiptID) throws KakaocertException {
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignIdentity/Verify/" + receiptID, ClientCode, null,
				VerifyResult.class);
	}

	@Override
	public VerifyResult verifyCMS(String ClientCode, String receiptID) throws KakaocertException {
		if(null == ClientCode || ClientCode.length() == 0 ) throw new KakaocertException(-99999999, "이용기관코드가 입력되지 않았습니다.");
		if(null == receiptID || receiptID.length() == 0 ) throw new KakaocertException(-99999999, "접수아이디가 입력되지 않았습니다.");
		
		return httpget("/SignDirectDebit/Verify/" + receiptID, ClientCode, null,
				VerifyResult.class);
	}

	

	
	
	
}
