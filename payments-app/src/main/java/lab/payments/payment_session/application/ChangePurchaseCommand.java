package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.Purchase;

public record ChangePurchaseCommand(SessionWriteContext context, Purchase purchase) {}
