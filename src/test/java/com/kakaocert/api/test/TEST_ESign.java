package com.kakaocert.api.test;

import static org.junit.Assert.assertNotNull;

import java.io.FileWriter;
import java.io.PrintWriter;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.kakaocert.api.KakaocertException;
import com.kakaocert.api.KakaocertService;
import com.kakaocert.api.KakaocertServiceImp;
import com.kakaocert.api.ResponseESign;
import com.kakaocert.api.VerifyResult;
import com.kakaocert.api.esign.RequestESign;
import com.kakaocert.api.esign.ResultESign;
import com.kakaocert.api.test.config.TestConfig;
import com.kakaocert.api.test.config.TestUserInfo;
import com.kakaocert.api.test.util.PrettyPrint;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TEST_ESign {

	private KakaocertService kakaocertService;
	
	private static String receiptID = "";
	
	@Before
	public void setup() {
		KakaocertServiceImp service = new KakaocertServiceImp();
		service.setLinkID(TestConfig.testLinkID);
		service.setSecretKey(TestConfig.testSecretKey);
//		service.setUseStaticIP(false);	// 기본값
		
		kakaocertService = service;
	}
	
	/**
	 * 서비스 null 체크
	 */
	@Test
	public void test0_serverNullCheck() {
		assertNotNull("서비스 널 체크", kakaocertService);
	}
	
	
	@Test
	public void test1_requestESign_TEST() throws KakaocertException{
		try {
			RequestESign request = new RequestESign();
			
			//기본정보
			request.setCallCenterNum("1600-9999");
			request.setExpires_in(60);
			request.setToken("서명전문테스트");
			
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
			
			ResponseESign response = kakaocertService.requestESign(TestConfig.ClientCode, request, false);
			receiptID = response.getReceiptId();
			PrettyPrint.setTitleNValue("승인신청시작", "!");
			PrettyPrint.setTitleNValue("접수아이디", receiptID);
			PrettyPrint.setTitleNValue("카카오톡 트랜잭션아이디", response.getTx_id());
			PrettyPrint.print();
			
		} catch(KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void test2_getESignResult_TEST() throws KakaocertException {
		try {
			

			int veryfiedFlag = 0;
			while(true) {
				ResultESign result = kakaocertService.getESignState(TestConfig.ClientCode, receiptID);
				
				if(result.getState() == 0) {
					if(veryfiedFlag == 0) {
						PrettyPrint.setTitleNValue("인증이 완료되지 않았습니다.", "!");
						prettyPrint(result);
						veryfiedFlag++;
					}
				}else if(result.getState() == 1) {
					PrettyPrint.setTitleNValue("정상처리되었습니다.", "!");
					prettyPrint(result);
					break;
				}else {
					PrettyPrint.setTitleNValue("만료되었습니다.", "!");
					prettyPrint(result);
					break;
				}
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		} catch (KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void tset3_verifyESIGN_TEST() throws KakaocertException, Exception {
		try {
			VerifyResult result = kakaocertService.verifyESign(TestConfig.ClientCode, receiptID);

			PrettyPrint.setTitleNValue("접수아이디", result.getReceiptId());
			PrettyPrint.setTitleNValue("전자서명데이터전문", result.getSignedData());
			PrettyPrint.print();
			
		        PrintWriter outputStream = null;
		 
		        try {
		            outputStream = new PrintWriter(new FileWriter("characteroutput.txt"));
	                outputStream.println(result.getSignedData());
		        } finally {
		            if (outputStream != null) {
		                outputStream.close();
		            }
		        }
			
			
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
			request.isAppUseYN();
			
			ResponseESign receiptID = kakaocertService.requestESign(TestConfig.ClientCode, request, true);
			System.out.println(receiptID.getReceiptId());
			System.out.println(receiptID.getTx_id());
			
		} catch(KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	
	private void prettyPrint(ResultESign result) {
		
		PrettyPrint.setTitleNValue("접수아이디",result.getReceiptID());
		PrettyPrint.setTitleNValue("이용기관코드",result.getClientCode());
		PrettyPrint.setTitleNValue("이용기관명",result.getClientName());
		PrettyPrint.setTitleNValue("별칭",result.getSubClientName());
		PrettyPrint.setTitleNValue("별칭코드",result.getSubClientCode());
		PrettyPrint.setTitleNValue("상태",String.valueOf(result.getState()));
		PrettyPrint.setTitleNValue("인증요청만료시간",String.valueOf(result.getExpires_in()));
		PrettyPrint.setTitleNValue("고객센터전화번호",result.getCallCenterNum());
		PrettyPrint.setTitleNValue("인증 메세지 제목",result.getTmstitle());
		PrettyPrint.setTitleNValue("인증요청 메세지 부가내용",result.getTmsmessage());
		PrettyPrint.setTitleNValue("인증서 발급유형 선택",String.valueOf(result.isAllowSimpleRegistYN()));
		PrettyPrint.setTitleNValue("수신자 실명확인 여부",String.valueOf(result.isVerifyNameYN()));
		PrettyPrint.setTitleNValue("카카오 인증서버 등록일시",result.getRequestDT());
		PrettyPrint.setTitleNValue("인증 만료일시",result.getExpireDT());
		PrettyPrint.setTitleNValue("인증요청 등록일시",result.getRegDT());
		PrettyPrint.setTitleNValue("수신자 카카오톡 인증메시지 확인일시",result.getViewDT());
		PrettyPrint.setTitleNValue("수신자 카카오톡 전자서명 완료일시",result.getCompleteDT());
		PrettyPrint.setTitleNValue("서명 검증일시",result.getVerifyDT());
		PrettyPrint.setTitleNValue("메모",result.getPayload());
		PrettyPrint.setTitleNValue("App to App 방식 이용 여부",String.valueOf(result.isAppUseYN()));
		PrettyPrint.setTitleNValue("카카오톡 트랜잭션아이디",result.getTx_id());
		PrettyPrint.print();
	}
	
}
