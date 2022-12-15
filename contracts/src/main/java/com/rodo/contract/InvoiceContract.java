package com.rodo.contract;


import com.rodo.states.InvoiceState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

// ************
// * Contract *
// ************
public class InvoiceContract implements Contract {
    // Used to identify our contract when building a transaction.
    public final static String ID = "com.rodo.contract.InvoiceContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        CommandWithParties<Commands> commandData = requireSingleCommand(tx.getCommands(), Commands.class);

        if (commandData.getValue() instanceof Commands.Issue) {
            requireThat(req -> {
                List<InvoiceState> output = tx.outputsOfType(InvoiceState.class);
                req.using("must be single output", output.size() == 1);
                req.using("Attachment ID must be stored in state", !output.get(0).getInvoiceAttachmentID().isEmpty());
                return null;
            });
        }
    }

    public interface Commands extends CommandData {
        class Issue implements Commands {
        }
    }
}
