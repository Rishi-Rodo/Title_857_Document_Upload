package com.corda.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.rodo.contract.InvoiceContract;
import com.rodo.states.InvoiceState;
import javassist.bytecode.ByteArray;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
public class SendAttachment extends FlowLogic<SignedTransaction> {

    private final static Logger logger = LoggerFactory.getLogger(SendAttachment.class);

    private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction");
    private final ProgressTracker.Step PROCESSING_TRANSACTION = new ProgressTracker.Step("PROCESS transaction");
    private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.");

    private final ProgressTracker progressTracker =
            new ProgressTracker(GENERATING_TRANSACTION, PROCESSING_TRANSACTION, FINALISING_TRANSACTION);

    private final Party receiver;
    private boolean unitTest = false;

    public SendAttachment(Party receiver) {
        this.receiver = receiver;
    }

    public SendAttachment(Party receiver, boolean unitTest) {
        this.receiver = receiver;
        this.unitTest = unitTest;
    }

    @Nullable
    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        // Obtain a reference to a notary we wish to use.
        /** Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)*/
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // Initiate transaction Builder
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

        // upload attachment via private method
        String path = System.getProperty("user.dir");
        System.out.println("Working Directory = " + path);
        String inputString = "Hello World!";
        byte[] byteArrray = inputString.getBytes();
        String vin = "JM1CR29LX60120246";
        String user_id = UUID.randomUUID().toString();

        //Change the path to "../test.zip" for passing the unit test.
        //because the unit test are in a different working directory than the running node.
        //String zipPath = unitTest ? "../test.zip" : "../../../test.zip";

        SecureHash attachmentHash = null;
        try {
            attachmentHash = SecureHash.parse(uploadAttachment(
                    vin,
                    getServiceHub(),user_id,
                    byteArrray)
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        // build transaction
        InvoiceState output = new InvoiceState(attachmentHash.toString(), ImmutableList.of(getOurIdentity(), receiver));
        InvoiceContract.Commands.Issue commandData = new InvoiceContract.Commands.Issue();
        transactionBuilder.addCommand(commandData, getOurIdentity().getOwningKey(), receiver.getOwningKey());
        transactionBuilder.addOutputState(output, InvoiceContract.ID);
        transactionBuilder.addAttachment(attachmentHash);
        transactionBuilder.verify(getServiceHub());

        // self signing
        progressTracker.setCurrentStep(PROCESSING_TRANSACTION);
        SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

        // counter parties signing
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);

        FlowSession session = initiateFlow(receiver);
        SignedTransaction fullySignedTransaction = subFlow(new CollectSignaturesFlow(signedTransaction, ImmutableList.of(session)));

        return subFlow(new FinalityFlow(fullySignedTransaction, ImmutableList.of(session)));
    }

    private String uploadAttachment(String fileName, ServiceHub service,String uploader, byte[] document_array) throws IOException {

        logger.info("byte Array:" + Arrays.toString(document_array));

        FileInputStream input = new FileInputStream(new ByteArrayInputStream(document_array).toString());

        logger.info("Input object" + input.toString());

        SecureHash attachmentHash = service.getAttachments().importAttachment(
                input,uploader,fileName);

        return attachmentHash.toString();
    }
}
