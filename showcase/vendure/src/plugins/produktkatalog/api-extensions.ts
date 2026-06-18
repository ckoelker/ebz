import gql from 'graphql-tag';

/**
 * Gemeinsame Typdefinitionen (Personen/Bewertungen) für Admin- und Shop-API.
 */
const sharedTypes = gql`
    type Ansprechpartner {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        crmPersonId: String!
        name: String!
        email: String
        telefon: String
        fotoAssetId: String
    }

    type Dozent {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        crmPersonId: String!
        name: String!
        vita: String
        fotoAssetId: String
    }

    type Bewertung {
        id: ID!
        createdAt: DateTime!
        updatedAt: DateTime!
        productId: ID!
        autor: String!
        text: String!
        sterne: Int!
        datum: DateTime!
    }

    type BewertungUebersicht {
        productId: ID!
        anzahl: Int!
        durchschnitt: Float!
        items: [Bewertung!]!
    }
`;

/**
 * Admin-API: Lese-Queries + idempotente Upserts (Shop-Initializer/CRM-Personen-Sync).
 * Schlüssel sind {@code crmPersonId} (Personen) bzw. (productId, autor) (Bewertung).
 */
export const adminApiExtensions = gql`
    ${sharedTypes}

    input UpsertAnsprechpartnerInput {
        crmPersonId: String!
        name: String!
        email: String
        telefon: String
        fotoAssetId: String
    }

    input UpsertDozentInput {
        crmPersonId: String!
        name: String!
        vita: String
        fotoAssetId: String
    }

    input UpsertBewertungInput {
        productId: ID!
        autor: String!
        text: String!
        sterne: Int!
        datum: DateTime
    }

    extend type Query {
        ansprechpartner: [Ansprechpartner!]!
        dozenten: [Dozent!]!
        bewertungen(productId: ID!): BewertungUebersicht!
    }

    extend type Mutation {
        upsertAnsprechpartner(input: UpsertAnsprechpartnerInput!): Ansprechpartner!
        upsertDozent(input: UpsertDozentInput!): Dozent!
        upsertBewertung(input: UpsertBewertungInput!): Bewertung!
    }
`;

/** Shop-API: nur Lese-Zugriff auf Bewertungen (Storefront). */
export const shopApiExtensions = gql`
    ${sharedTypes}

    extend type Query {
        bewertungen(productId: ID!): BewertungUebersicht!
    }
`;
