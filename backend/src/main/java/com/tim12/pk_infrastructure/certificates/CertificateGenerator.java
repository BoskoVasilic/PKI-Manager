package com.tim12.pk_infrastructure.certificates;

import com.tim12.pk_infrastructure.model.Issuer;
import com.tim12.pk_infrastructure.model.Subject;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

@Component
public class CertificateGenerator {

    public CertificateGenerator() {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static X509Certificate generateCertificate(
            Subject subject,
            Issuer issuer,
            Date startDate,
            Date endDate,
            String serialNumber,
            boolean isCA,
            boolean keyCertSign,
            boolean cRLSign,
            boolean digitalSig,
            boolean keyEncipher,
            boolean serverAuth
    ) {
        try {
            JcaContentSignerBuilder builder = new JcaContentSignerBuilder("SHA256WithRSAEncryption");
            builder = builder.setProvider("BC");

            ContentSigner contentSigner = builder.build(issuer.getPrivateKey());

            X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                    issuer.getX500Name(),
                    new BigInteger(serialNumber),
                    startDate,
                    endDate,
                    subject.getX500Name(),
                    subject.getPublicKey()
            );

            //ekstenzije

            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();

            certGen.addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extUtils.createSubjectKeyIdentifier(subject.getPublicKey())
            );

            certGen.addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extUtils.createAuthorityKeyIdentifier(issuer.getPublicKey())
            );

            certGen.addExtension(
                    Extension.basicConstraints,
                    true,  // critical
                    new BasicConstraints(isCA)
            );

            int keyUsageBits = 0;
            if (keyCertSign)  keyUsageBits |= KeyUsage.keyCertSign;
            if (cRLSign)      keyUsageBits |= KeyUsage.cRLSign;
            if (digitalSig)   keyUsageBits |= KeyUsage.digitalSignature;
            if (keyEncipher)  keyUsageBits |= KeyUsage.keyEncipherment;

            if (keyUsageBits != 0) {
                certGen.addExtension(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(keyUsageBits)
                );
            }

            if (serverAuth) {
                certGen.addExtension(
                        Extension.extendedKeyUsage,
                        false,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
                );
            }

            X509CertificateHolder certHolder = certGen.build(contentSigner);

            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
            certConverter = certConverter.setProvider("BC");

            return certConverter.getCertificate(certHolder);

        } catch (OperatorCreationException e) {
            throw new RuntimeException("Greška pri kreiranju content signera: " + e.getMessage(), e);
        } catch (CertificateException e) {
            throw new RuntimeException("Greška pri konverziji sertifikata: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algoritam nije pronađen: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Neočekivana greška pri generisanju sertifikata: " + e.getMessage(), e);
        }
    }
}