package com.kakaocert.api.test;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.kakaocert.api.KakaocertException;
import com.kakaocert.api.KakaocertService;
import com.kakaocert.api.KakaocertServiceImp;
import com.kakaocert.api.ResponseESign;
import com.kakaocert.api.VerifyResult;
import com.kakaocert.api.esign.RequestESign;
import com.kakaocert.api.esign.ResultESign;
import com.kakaocert.api.test.config.TestConfig;
import com.kakaocert.api.test.config.TestUserInfo;

public class TEST_ESign {

	private KakaocertService kakaocertService;
	
	@Before
	public void setup() {
		KakaocertServiceImp service = new KakaocertServiceImp();
		service.setLinkID(TestConfig.testLinkID);
		service.setSecretKey(TestConfig.testSecretKey);
		service.setUseStaticIP(false);
		
		kakaocertService = service;
	}
	
	/**
	 * 서비스 null 체크
	 */
	@Test
	public void Test_serverNullCheck() {
		assertNotNull("서비스 널 체크", kakaocertService);
	}
	
	
	@Test
	public void requestESign_TEST() throws KakaocertException{
		try {
			RequestESign request = new RequestESign();
			
			//기본정보
			request.setCallCenterNum("1600-9999");
			request.setExpires_in(60);
			request.setToken("token value");
			
			//수신자정보
			request.setReceiverBirthDay(TestUserInfo.birth);
			request.setReceiverHP(TestUserInfo.tel);
			request.setReceiverName(TestUserInfo.name);
				
			//부가정보
			request.setTMSMessage("인증 테스트입니다.");
			request.setTMSTitle("인증 테스트");
			request.setSubClientID("");	//별칭 코드
			
			request.setVerifyNameYN(true);	//실명확인여부
			
			request.setPayLoad(null);
			
			request.setAllowSimpleRegistYN(true);
			
			ResponseESign response = kakaocertService.requestESign("020040000001", request, false);
			System.out.println(response.getReceiptId());
			System.out.println(response.getTx_id());
			
		} catch(KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void getESignResult_TEST() throws KakaocertException {
		try {
			ResultESign result = kakaocertService.getESignState("020040000001", "021030312114400001");
			
			System.out.println(result.getCallCenterNum());
			System.out.println(result.getReceiptID());
			System.out.println(result.getRegDT());	//2
			System.out.println(result.getState());
			System.out.println(result.getExpires_in());
			System.out.println(result.isAllowSimpleRegistYN());
			System.out.println(result.isVerifyNameYN());
			System.out.println(result.getPayload());
			System.out.println(result.getRequestDT());
			System.out.println(result.getExpireDT());
			System.out.println(result.getClientCode());
			System.out.println(result.getClientName());
			System.out.println(result.getTmstitle());
			System.out.println(result.getTmsmessage());
			
			System.out.println(result.getSubClientCode());
			System.out.println(result.getSubClientName());
			System.out.println(result.getRequestDT());
			System.out.println(result.getViewDT());
			System.out.println(result.getCompleteDT());
			System.out.println(result.getVerifyDT());
			System.out.println(result.isAppUseYN());
		} catch (KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void verifyESIGN_TEST() throws KakaocertException {
		try {
			VerifyResult result = kakaocertService.verifyESign("020040000001", "020090815353800001");
			
			System.out.println(result.getReceiptId());
			System.out.println(result.getSignedData());
			
		} catch (KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void requestESignApp_TEST() throws KakaocertException{
		try {
			RequestESign request = new RequestESign();
			request.setAllowSimpleRegistYN(true);
			request.setVerifyNameYN(true);
			request.setCallCenterNum("1600-9999");
			request.setExpires_in(1);
			request.setPayLoad(null);
			request.setReceiverBirthDay(TestUserInfo.birth);
			request.setReceiverHP(TestUserInfo.tel);
			request.setReceiverName(TestUserInfo.name);
			request.setTMSMessage(null);
			request.setSubClientID("");
			request.setTMSTitle("TMS Title");
			request.setToken("token value");
			
			ResponseESign receiptID = kakaocertService.requestESign("020040000001", request, true);
			System.out.println(receiptID.getReceiptId());
			System.out.println(receiptID.getTx_id());
			
		} catch(KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	
}
