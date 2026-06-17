package com.tim12.pk_infrastructure.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url:https://localhost:4200}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendActivationEmail(String toEmail, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(toEmail);
            helper.setSubject("Activate your account");

            String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif; background-color:#f4f4f4; padding:20px;">
                <div style="max-width:600px; margin:auto; background:white; padding:20px; border-radius:10px;">
                    
                    <h2 style="color:#333;">Welcome!</h2>
                    
                    <p>Thanks for registering. Please activate your account by clicking the button below:</p>
                    
                    <div style="text-align:center; margin:30px 0;">
                        <a href="%s" 
                           style="background-color:#4CAF50; color:white; padding:12px 20px; text-decoration:none; border-radius:5px; font-weight:bold;">
                            Activate Account
                        </a>
                    </div>

                    <p style="color:#777;">This link is valid for 24 hours.</p>

                    <hr/>
                    <p style="font-size:12px; color:#aaa;">
                        If the button doesn't work, copy and paste this link into your browser:
                        <br/>
                        <a href="%s">%s</a>
                    </p>

                </div>
            </body>
            </html>
            """.formatted(link, link, link);

            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendMail(String toEmail, String subject, String body) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }

    public void sendRegistrationActivationEmail(String toEmail, String token) {
        String activationUrl = baseUrl + "/verify-account?token=" + token + "&type=registration";

        String html = """
                <div style="font-family: monospace; background: #0b0b18; color: #e8e8f0; padding: 32px; border-radius: 8px;">
                  <div style="margin-bottom: 24px;">
                    <span style="color: #10b981; font-weight: bold; font-size: 14px; letter-spacing: 2px;">
                      PKI SYSTEM
                    </span>
                  </div>
                  <h2 style="color: #ffffff; margin: 0 0 8px 0; font-size: 20px;">Account Activation</h2>
                  <p style="color: #6b7280; margin: 0 0 24px 0; font-size: 14px;">
                    To complete registration, please confirm your identity using your private key.
                  </p>
                  <a href="%s"
                     style="display: inline-block; background: #059669; color: white; padding: 12px 24px;
                            text-decoration: none; border-radius: 6px; font-size: 12px;
                            letter-spacing: 2px; text-transform: uppercase;">
                    Activate Account
                  </a>
                  <p style="color: #374151; margin: 24px 0 0 0; font-size: 12px;">
                    This link is valid for 24 hours and can only be used once.<br>
                    If you did not initiate this registration, please ignore this email.
                  </p>
                </div>
                """.formatted(activationUrl);

        sendEmail(toEmail, "PKI Account Activation", html);
    }


    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = baseUrl + "/verify-account?token=" + token + "&type=password-reset";

        String html = """
                <div style="font-family: monospace; background: #0b0b18; color: #e8e8f0; padding: 32px; border-radius: 8px;">
                  <div style="margin-bottom: 24px;">
                    <span style="color: #10b981; font-weight: bold; font-size: 14px; letter-spacing: 2px;">
                      PKI SYSTEM
                    </span>
                  </div>
                  <h2 style="color: #ffffff; margin: 0 0 8px 0; font-size: 20px;">Password Recovery</h2>
                  <p style="color: #6b7280; margin: 0 0 24px 0; font-size: 14px;">
                    We received a password reset request. To proceed, confirm your identity with your private key.
                  </p>
                  <a href="%s"
                     style="display: inline-block; background: #d97706; color: white; padding: 12px 24px;
                            text-decoration: none; border-radius: 6px; font-size: 12px;
                            letter-spacing: 2px; text-transform: uppercase;">
                    Reset Password
                  </a>
                  <p style="color: #374151; margin: 24px 0 0 0; font-size: 12px;">
                    This link is valid for 1 hour.<br>
                    If you did not request a password reset, please ignore this email.
                  </p>
                </div>
                """.formatted(resetUrl);

        sendEmail(toEmail, "Password Reset — PKI System", html);
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error sending email: " + e.getMessage(), e);
        }
    }
}
