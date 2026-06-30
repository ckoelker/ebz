import gql from 'graphql-tag';

/**
 * Admin-API des Seminar-Kosten-Plugins (M2). Erscheint in der Admin-API und im
 * Vendure-Dashboard; das Controlling-Warehouse liest die Tabelle später per dlt.
 */
export const adminApiExtensions = gql`
    type SeminarCost {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        productVariantId: ID!
        costType: String!
        label: String!
        amount: Int!
        currencyCode: String!
        isVariable: Boolean!
        perParticipant: Boolean!
    }

    input CreateSeminarCostInput {
        productVariantId: ID!
        costType: String!
        label: String!
        amount: Int!
        currencyCode: String
        isVariable: Boolean!
        perParticipant: Boolean!
    }

    input UpdateSeminarCostInput {
        id: ID!
        costType: String
        label: String
        amount: Int
        currencyCode: String
        isVariable: Boolean
        perParticipant: Boolean
    }

    extend type Query {
        "Alle Kostenpositionen einer Seminar-Variante"
        seminarCosts(productVariantId: ID!): [SeminarCost!]!
    }

    extend type Mutation {
        createSeminarCost(input: CreateSeminarCostInput!): SeminarCost!
        updateSeminarCost(input: UpdateSeminarCostInput!): SeminarCost!
        deleteSeminarCost(id: ID!): Boolean!
    }
`;
