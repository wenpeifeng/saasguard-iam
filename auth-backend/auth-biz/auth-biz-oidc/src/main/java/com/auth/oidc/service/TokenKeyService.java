package com.auth.oidc.service;

import com.auth.common.exception.BusinessException;
import com.auth.oidc.entity.TokenKey;
import com.auth.oidc.mapper.TokenKeyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * OIDC 签名密钥服务 —— RS256 密钥对的加载/生成/发布。
 * <p>
 * 策略：启动后懒加载"签发中"密钥；库里没有则生成 2048 位密钥对入库
 * （kid=UUID，JWKS 按 kid 发布公钥）。轮换预留：将旧行 status 置 0 后
 * 再生成新行即可，验签按 JWT header.kid 从全部有效密钥中查找。
 * 私钥 PKCS#8 PEM 存库仅供开发/演示，生产建议接 KMS。
 */
@Slf4j
@Service
public class TokenKeyService {

    private final TokenKeyMapper tokenKeyMapper;

    /** 签发中 */
    private static final int STATUS_ACTIVE = 1;

    private volatile TokenKey activeKey;

    public TokenKeyService(TokenKeyMapper tokenKeyMapper) {
        this.tokenKeyMapper = tokenKeyMapper;
    }

    /**
     * 取当前签发密钥（懒加载，无则自动生成）。
     */
    public synchronized TokenKey activeKey() {
        if (activeKey != null) {
            return activeKey;
        }
        TokenKey found = tokenKeyMapper.selectOne(new LambdaQueryWrapper<TokenKey>()
                .eq(TokenKey::getStatus, STATUS_ACTIVE).orderByDesc(TokenKey::getId).last("LIMIT 1"));
        if (found == null) {
            found = generate();
        }
        activeKey = found;
        log.info("[OIDC] 签名密钥就绪 kid={}（{}生成）", found.getKid(), found.getCreateTime() != null ? "库内" : "新建");
        return found;
    }

    /** 按 kid 查密钥（验签用；含已轮换密钥） */
    public TokenKey byKid(String kid) {
        return tokenKeyMapper.selectOne(new LambdaQueryWrapper<TokenKey>()
                .eq(TokenKey::getKid, kid).last("LIMIT 1"));
    }

    /** 生成新密钥对并入库 */
    private TokenKey generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            TokenKey key = new TokenKey();
            key.setKid(UUID.randomUUID().toString());
            key.setAlgorithm("RS256");
            key.setPublicKey(toPem("PUBLIC KEY", pair.getPublic().getEncoded()));
            key.setPrivateKey(toPem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            key.setStatus(STATUS_ACTIVE);
            tokenKeyMapper.insert(key);
            return key;
        } catch (Exception e) {
            throw new BusinessException("生成 OIDC 签名密钥失败：%s", e.getMessage());
        }
    }

    /** 公钥 PEM → PublicKey 对象 */
    public PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new BusinessException("解析公钥失败：%s", e.getMessage());
        }
    }

    /** 私钥 PEM → PrivateKey 对象 */
    public PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new BusinessException("解析私钥失败：%s", e.getMessage());
        }
    }

    /** PEM → DER */
    private static byte[] decodePem(String pem) {
        String base64 = pem.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    /** DER → PEM */
    private static String toPem(String type, byte[] der) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        String base64 = Base64.getEncoder().encodeToString(der);
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(base64.length(), i + 64)).append('\n');
        }
        sb.append("-----END ").append(type).append("-----");
        return sb.toString();
    }
}
