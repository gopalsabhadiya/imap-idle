
package com.example.imapidle.config;

import com.example.imapidle.dto.EmailContent;
import com.example.imapidle.dto.file.FileHolder;
import com.example.imapidle.dto.file.SupportedFile;
import com.example.imapidle.util.NullSafetyProvider;
import com.example.imapidle.util.EmailAttachmentNameUtil;
import com.sun.mail.imap.IMAPMessage;
import jakarta.mail.*;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mail.ImapIdleChannelAdapter;
import org.springframework.integration.mail.ImapMailReceiver;
import org.springframework.messaging.Message;
import org.springframework.messaging.SubscribableChannel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Configuration
@EnableIntegration
public class MailReceiverConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MailReceiverConfiguration.class);

    @ServiceActivator(inputChannel = "receiveEmailPubSubChannelChannel")
    public void receive(Message<IMAPMessage> messageContainer) throws MessagingException, IOException {
        jakarta.mail.Message message = messageContainer.getPayload();
        Folder folder = message.getFolder();

        folder.open(Folder.READ_WRITE);

        jakarta.mail.Message[] messages = folder.getMessages();

        Arrays.asList(messages).stream().filter(emailMessage -> {
            MimeMessage currentMessage = (MimeMessage) emailMessage;
            try {
                return currentMessage.getMessageID().equalsIgnoreCase(((MimeMessage) message).getMessageID());
            } catch (MessagingException e) {
                log.error("Error occurred during process message", e);
                return false;
            }
        }).forEach(msg -> {
            try {
                extractMail(msg);
            } catch (MessagingException | IOException e) {
                throw new RuntimeException(e);
            }
        });

        folder.close(true);
    }

    public void extractMail(jakarta.mail.Message msg) throws MessagingException, IOException {
        EmailContent email = EmailContent.builder()
            .size(msg.getSize())
            .messageNumber(msg.getMessageNumber())
            .folder(msg.getFolder().getFullName())
            .flags(msg.getFlags().toString())
            .sentDate(msg.getSentDate())
            .receivedDate(msg.getReceivedDate())
            .from(NullSafetyProvider.arrayToList(msg.getFrom()).stream().map(Address::toString).collect(Collectors.toList()))
            .to(NullSafetyProvider.arrayToList(msg.getRecipients(jakarta.mail.Message.RecipientType.TO)).stream().map(Address::toString).collect(Collectors.toList()))
            .cc(NullSafetyProvider.arrayToList(msg.getRecipients(jakarta.mail.Message.RecipientType.CC)).stream().map(Address::toString).collect(Collectors.toList()))
            .bcc(NullSafetyProvider.arrayToList(msg.getRecipients(jakarta.mail.Message.RecipientType.BCC)).stream().map(Address::toString).collect(Collectors.toList()))
            .subject(msg.getSubject())
            .attachmentList(new ArrayList<>())
            .build();

        if (msg.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart part = (MimeBodyPart) multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    log.info(part.getContentType());
                    if(part.getContentType().toLowerCase().contains("application/pdf")) {
                        log.info("Found PDF attachment");
                        InputStream attachment = part.getRawInputStream();
                        email.getAttachmentList().add(
                            new FileHolder(
                                EmailAttachmentNameUtil.getFileNameFromContentType(part.getContentType()),
                                SupportedFile.PDF_INVOICE,
                                attachment.readAllBytes()
                            )
                        );
                        attachment.close();
                    }
                }
                else if (part.getContentType().toLowerCase().contains("multipart/alternative")) {
                    MimeMultipart content = ((MimeMultipart) part.getContent());
                    BodyPart textBody = content.getBodyPart(0);
                    BodyPart htmlBody = content.getBodyPart(1);
                    email.setContent(textBody.getContent().toString());
                    email.setHtmlContent(htmlBody.getContent().toString());
                }
            }
        }
        //Process your email further here.
    }

    private void fetchMessagesInFolder(Folder folder, jakarta.mail.Message[] messages) throws MessagingException {
        FetchProfile contentsProfile = new FetchProfile();
        contentsProfile.add(FetchProfile.Item.ENVELOPE);
        contentsProfile.add(FetchProfile.Item.CONTENT_INFO);
        contentsProfile.add(FetchProfile.Item.FLAGS);
        contentsProfile.add(FetchProfile.Item.SIZE);
        folder.fetch(messages, contentsProfile);
    }

    @Bean("receiveEmailPubSubChannelChannel")
    public SubscribableChannel defaultChannel(@Qualifier("asyncTaskExecutor") Executor threadPoolTaskExecutor) {
        return new PublishSubscribeChannel(threadPoolTaskExecutor);
    }

    @Bean
    public ImapIdleChannelAdapter imapMailReceiver(@Value("imaps://${mail.imap.username}:${mail.imap.password}@${mail.imap.host}:${mail.imap.port}/inbox") String storeUrl) {
//        log.info("IMAP connection url: {}", storeUrl);
//

        Properties javaMailProperties = new Properties();
        javaMailProperties.put("mail.imap.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        javaMailProperties.put("mail.imap.socketFactory.fallback", false);
        javaMailProperties.put("mail.store.protocol", "imaps");
        javaMailProperties.put("mail.debug", true);
//
        ImapMailReceiver imapMailReceiver = new ImapMailReceiver(storeUrl);
        imapMailReceiver.setShouldMarkMessagesAsRead(true);
        imapMailReceiver.setShouldDeleteMessages(false);
        imapMailReceiver.setMaxFetchSize(10);
//        imapMailReceiver.setAutoCloseFolder(false);
        imapMailReceiver.setJavaMailProperties(javaMailProperties);
        imapMailReceiver.afterPropertiesSet();

        ImapIdleChannelAdapter imapIdleChannelAdapter = new ImapIdleChannelAdapter(imapMailReceiver);
        imapIdleChannelAdapter.setOutputChannelName("receiveEmailPubSubChannelChannel");

        return imapIdleChannelAdapter;
    }

}
