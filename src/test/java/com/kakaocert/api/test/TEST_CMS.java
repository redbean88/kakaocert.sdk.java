package com.kakaocert.api.test;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.kakaocert.api.KakaocertException;
import com.kakaocert.api.KakaocertService;
import com.kakaocert.api.KakaocertServiceImp;
import com.kakaocert.api.VerifyResult;
import com.kakaocert.api.cms.RequestCMS;
import com.kakaocert.api.cms.ResultCMS;
import com.kakaocert.api.test.config.TestConfig;
import com.kakaocert.api.test.config.TestUserInfo;
import com.kakaocert.api.test.util.PrettyPrint;
import com.kakaocert.api.test.util.decodeBase64Url;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TEST_CMS {
	
	private KakaocertService kakaocertService;
	
	private static String receiptID = "";
	
	
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
	public void test0_serverNullCheck() {
		assertNotNull("서비스 널 체크", kakaocertService);
		PrettyPrint.setTitleNValue("서비스가 정상적으로 생성되었습니다.", "1231");
		PrettyPrint.print();
	}
	
	@Test
	public void test1_request() throws KakaocertException{
		try {
			RequestCMS request = new RequestCMS();
			request.setCallCenterNum("1600-9999");
			request.setExpires_in(60);
			request.setReceiverBirthDay(TestUserInfo.birth);
			request.setReceiverHP(TestUserInfo.tel);
			request.setReceiverName(TestUserInfo.name);
			
			request.setBankAccountName("예금주명");
			request.setBankAccountNum("계좌번호");
			
			request.setBankCode("004"); //참여기관 코드
			
			request.setClientUserID("식별코드"); //이용기관에서 부여한 식별코드
			
			request.setSubClientID(TestConfig.SubClientID);	//별칭코드
			
			request.setTMSTitle("메시지명칭");
			request.setTMSMessage("메시지 내용");
			
			request.setAllowSimpleRegistYN(false);
			request.setVerifyNameYN(false);
			
			request.setPayLoad("메모용 데이터 입니다.");
			
			receiptID = kakaocertService.requestCMS(TestConfig.ClientCode, request);
			PrettyPrint.setTitleNValue("승인신청시작", "!");
			PrettyPrint.setTitleNValue("접수아이디", receiptID);
			PrettyPrint.print();
			
		} catch(KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	@Test
	public void test2_getResult() throws KakaocertException {
		try {

			int veryfiedFlag = 0;
			while(true) {
				ResultCMS result = kakaocertService.getCMSState(TestConfig.ClientCode, receiptID);
				
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
	public void test3_verifyCMS() throws KakaocertException {
		try {
			VerifyResult result = kakaocertService.verifyCMS(TestConfig.ClientCode, receiptID);
			
			PrettyPrint.setTitleNValue("접수아이디",result.getReceiptId());
			PrettyPrint.setTitleNValue("전자서명데이터전문",result.getSignedData());
			PrettyPrint.setTitleNValue("전자서명데이터원문",decodeBase64Url.run(result.getSignedData()));
			PrettyPrint.print();
						
		} catch (KakaocertException ke) {
			System.out.println(ke.getCode());
			System.out.println(ke.getMessage());
		}
	}
	
	private void prettyPrint(ResultCMS result) {
		
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
		PrettyPrint.print();
	}

}
