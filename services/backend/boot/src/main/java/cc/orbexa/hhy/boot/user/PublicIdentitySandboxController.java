package cc.orbexa.hhy.boot.user;

import cc.orbexa.hhy.access.identity.IdentitySandboxService;
import cc.orbexa.hhy.access.identity.IdentitySandboxService.Completion;
import cc.orbexa.hhy.access.identity.IdentitySandboxService.PageContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Camera-capable test-provider page. The controller does not accept or display identity data. */
@RestController
@ConditionalOnProperty(name = "hhy.identity-sandbox.enabled", havingValue = "true")
@RequestMapping("/public-api/v1/identity/sandbox")
public class PublicIdentitySandboxController {
    private final IdentitySandboxService service;

    public PublicIdentitySandboxController(IdentitySandboxService service) {
        this.service = service;
    }

    @GetMapping(value = "/liveness", produces = MediaType.TEXT_HTML_VALUE)
    public String liveness(
            @RequestParam String state, @RequestParam String returnUrl) {
        PageContext page = service.page(state, returnUrl);
        return html(page);
    }

    @PostMapping(value = "/complete", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> complete(
            @RequestParam String state,
            @RequestParam String returnUrl,
            @RequestParam String decision) {
        Completion completion = service.complete(state, returnUrl, decision);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, completion.returnUrl().toString())
                .build();
    }

    private static String html(PageContext page) {
        String state = htmlEscape(page.state());
        String returnUrl = htmlEscape(page.returnUrl().toString());
        return """
                <!doctype html><html lang="zh-CN"><head>
                <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>人脸活体检测</title><style>
                *{box-sizing:border-box}body{margin:0;background:#10151d;color:#fff;font-family:sans-serif}
                main{min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:24px 20px}
                h1{font-size:24px;margin:8px 0}p{color:#c8d0db;margin:6px 0 20px;text-align:center}
                .frame{width:min(78vw,360px);aspect-ratio:3/4;border:3px solid #2684ff;border-radius:48%%;overflow:hidden;background:#202a36;display:grid;place-items:center}
                video{width:100%%;height:100%%;object-fit:cover;transform:scaleX(-1)}
                .controls{width:min(92vw,460px);display:grid;gap:12px;margin-top:24px}
                button{min-height:52px;border:0;border-radius:26px;font-size:17px;font-weight:700}
                .primary{background:#1677ff;color:#fff}.secondary{background:#2a3442;color:#e7ebf0}
                </style></head><body><main><h1>人脸活体检测</h1>
                <p id="hint">请保持正脸清晰，并按提示完成检测</p>
                <div class="frame"><video id="camera" autoplay muted playsinline></video></div>
                <div class="controls"><button id="start" class="primary" type="button">开始检测</button>
                <form method="post" action="/public-api/v1/identity/sandbox/complete">
                <input type="hidden" name="state" value="%s"><input type="hidden" name="returnUrl" value="%s">
                <button class="primary" name="decision" value="PASS" type="submit">完成检测</button>
                <button class="secondary" name="decision" value="REJECT" type="submit">无法完成</button>
                </form></div></main><script>
                document.getElementById('start').onclick=async()=>{try{const s=await navigator.mediaDevices.getUserMedia({video:{facingMode:'user'},audio:false});document.getElementById('camera').srcObject=s;document.getElementById('hint').textContent='请正对屏幕，保持面部清晰';document.getElementById('start').hidden=true}catch(e){document.getElementById('hint').textContent='需要允许相机权限才能继续'}};
                </script></body></html>
                """.formatted(state, returnUrl);
    }

    private static String htmlEscape(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
