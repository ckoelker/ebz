import gql from 'graphql-tag';

export const adminApiExtensions = gql`
    type Installment {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        orderId: ID!
        orderCode: String!
        variantName: String!
        sequence: Int!
        totalCount: Int!
        amount: Int!
        currencyCode: String!
        dueDate: DateTime!
        status: String!
    }

    extend type Query {
        installmentsForOrder(orderId: ID!): [Installment!]!
    }

    extend type Mutation {
        "Materialisiert den Ratenplan einer Bestellung als Installment-Datensätze"
        materializeInstallments(orderId: ID!): Int!
        "Interner Rechnungslauf: stellt alle fälligen Raten in Rechnung (scheduled → invoiced)"
        runRecurringInvoiceRun: Int!
    }
`;
