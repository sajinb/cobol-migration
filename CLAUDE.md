You are an expert in Agentic AI development, read the below COBOL migration guide
Use Langraph and Langchain
Use local Neo4j (bolt://localhost:7687, HTTP API on http://localhost:7474)
Prepare proper modular structure for Agents


# COBOL Migration Guide

**1st Step in migration of COBOL is understanding COBOL code structure**

COBOL code has to be parsed. Below are the open-source options available.

| **Need** | **ProLeap** | **MAPA** |
|---|---|---|
| COBOL parser & semantic model | Strong AST/ASG for COBOL; EXEC blocks extracted as text (focus on COBOL semantics) | MAPA provides grammars for CICS/DB2/IMS/JCL and portfolio extraction |
| CICS/DB2/IMS/JCL coverage | Limited (as plain text for EXEC) | **MAPA:** explicit grammars & cross‑asset call trees |
| Portfolio call graph (system‑wide) | COBOL‑only calls | **MAPA** excels (CICS LINK/XCTL, DB2 calls, JCL job→step→program) |

> **Note:** MAPA is built on **top of ProLeap** — it uses ProLeap's parser internally and adds the analysis layer that produces the CSV output. So you're already getting ProLeap's parsing accuracy.

The migration plan is designed assuming:
- Hundreds of COBOL programs
- Thousands of copybooks
- Complex inter-program CALL graphs
- Shared data stores (VSAM files, DB2 tables)

The graph is what makes this tractable at that scale — not the LLM alone.

---

## Pipeline Overview

```
COBOL Source Files
 │
 ▼
 MAPA JAR ──────────────────► result.csv (structural data)
 │
 ▼
 Neo4j Import (Cypher)
 │
 ▼
 Graph DB (nodes + edges)
 programs, copybooks, paragraphs,
 CALL chains, data items, files
 │
 ┌─────────┴─────────┐
 │ Graph RAG Query │
 │ (relevant subgraph│
 │ per migration │
 │ unit) │
 └─────────┬─────────┘
 │
 ▼
 LLM Prompt
 (COBOL context +
 Spring Boot target)
 │
 ▼
 Java / Spring Boot Microservice
```

### Your Custom Work (3 layers)

| **Layer** | **What you build** |
|---|---|
| **Neo4j import** | Cypher scripts to map CSV rows → nodes/relationships |
| **Graph RAG** | Query logic to extract relevant subgraph per program/feature |
| **LLM prompting** | Prompt templates for COBOL→Java translation with graph context |

### Why the Plan Scales to Large/Complex COBOL

#### MAPA — Scales Well
- It's a static analysis tool built for enterprise COBOL
- Handles 500K+ line programs, deeply nested PERFORM chains, multiple copybooks, complex DATA DIVISION
- Output is always the same CSV structure regardless of program size — your import pipeline doesn't change

#### Neo4j — Scales Well
- Graph databases are built for deeply connected data
- A 500K-line COBOL program might produce **millions of nodes/edges** — Neo4j handles this natively
- Queries stay fast via indexed traversals (no full scans)

#### Graph RAG — This is the KEY insight for large programs

| **Without Graph RAG** | **With Graph RAG** |
|---|---|
| Dump entire 500K-line program into LLM | Query only the subgraph for PROGRAM-X |
| Exceeds context window, fails | 10–50 relevant nodes fed to LLM |
| No structure awareness | LLM sees typed relationships (CALLS, COPIES, READS) |

#### LLM Prompting — Adapts per chunk
- You never migrate the whole program at once
- You migrate **paragraph by paragraph** or **program by program**
- The graph tells you the **boundaries and dependencies** for each unit

---

## What Gets Harder at Scale (and how to handle it)

| **Challenge** | **Mitigation** |
|---|---|
| Circular CALL chains | Detect cycles in Neo4j before migration, resolve order |
| Shared copybooks used by 100s of programs | Model copybooks as shared nodes; generate shared Java DTOs/entities once |
| Implicit data flow (WORKING-STORAGE shared across paragraphs) | Graph captures data item usage; map to Spring service state carefully |
| Business logic buried in deeply nested PERFORMs | Graph traversal unrolls the call tree for the LLM |
| Multiple programs sharing the same file/DB | Identify in graph → becomes a candidate microservice boundary |

---

## What do you want to tackle first?
- **Neo4j schema design** — what nodes/relationships to model from the CSV
- **Cypher import scripts** — loading the CSV into Neo4j
- **Graph RAG query strategy** — how to slice the graph for LLM context
- **LLM prompt design** — structuring the migration prompts

---

## Business Logic Extraction Strategy

### Where COBOL Business Logic Lives
```
COBOL Program
├── DATA DIVISION
│   ├── WORKING-STORAGE ← shared state between paragraphs
│   ├── FILE SECTION ← I/O data structures
│   └── LINKAGE SECTION ← input/output parameters (for CALLed programs)
│
├── PROCEDURE DIVISION
│   ├── Paragraphs ← units of logic (like methods)
│   ├── PERFORM chains ← control flow (like method calls)
│   ├── CALL statements ← external program calls
│   └── EXEC SQL / EXEC CICS ← DB and transaction calls
│
└── COPY statements ← shared data/logic from copybooks
```

### Extraction Steps in the Pipeline

#### Step 1 — MAPA Identifies the Units
MAPA's CSV output gives you:
- Every **paragraph** and its line range
- Every **PERFORM** (who calls whom)
- Every **CALL** (inter-program dependencies)
- Every **COPY** (which copybooks are used)
- Every **data item** in WORKING-STORAGE

#### Step 2 — Neo4j Models the Logic Graph
```
(Paragraph)-[:PERFORMS]->(Paragraph)
(Paragraph)-[:READS]->(DataItem)
(Paragraph)-[:WRITES]->(DataItem)
(Paragraph)-[:CALLS]->(Program)
(Program)-[:COPIES]->(Copybook)
(Paragraph)-[:EXECUTES_SQL]->(Table)
```

#### Step 3 — Graph RAG Extracts a Migration Unit

```cypher
// Get everything needed to migrate one paragraph
MATCH (p:Paragraph {name: 'CALCULATE-PREMIUM'})
OPTIONAL MATCH (p)-[:PERFORMS*]->(child:Paragraph)
OPTIONAL MATCH (p)-[:READS|WRITES]->(d:DataItem)
OPTIONAL MATCH (p)-[:CALLS]->(ext:Program)
RETURN p, child, d, ext
```

This subgraph becomes the **LLM context** — not the entire program.

#### Step 4 — LLM Translates with Full Context

**Prompt skeleton:**
```
You are migrating COBOL to Spring Boot.
COBOL Paragraph: CALCULATE-PREMIUM
Source code: [raw COBOL lines]
Dependencies:
- Calls: VALIDATE-POLICY (external program)
- Reads: WS-POLICY-TYPE, WS-BASE-RATE, WS-AGE-FACTOR
- Writes: WS-FINAL-PREMIUM
- Performs: APPLY-DISCOUNT, APPLY-TAX
Data structures:
[WORKING-STORAGE items from graph]
Generate a Java method inside PremiumService with:
- Proper input parameters (replace WORKING-STORAGE with method args)
- Call to PolicyValidationService for VALIDATE-POLICY
- Return the calculated premium
```

---

## Logic → Microservice Mapping

| **COBOL Construct** | **Java/Spring Boot Equivalent** |
|---|---|
| Program (top-level) | `@Service` class |
| Paragraph | Method inside service |
| PERFORM chain | Method calls |
| WORKING-STORAGE | Method parameters or service-scoped state |
| CALL to external program | `@FeignClient` or REST call to another microservice |
| Copybook (data) | Java DTO / POJO (shared library) |
| Copybook (logic) | Shared utility service |
| EXEC SQL | JPA Repository / `@Query` |
| EXEC CICS | Spring transaction (`@Transactional`) |
| File I/O (VSAM) | Spring Batch / JPA |

---

## Key Challenge: WORKING-STORAGE
This is the hardest part — in COBOL, paragraphs share mutable state via WORKING-STORAGE implicitly. In Java, you must make this explicit:

```
Paragraph A writes WS-RESULT
Paragraph B reads WS-RESULT
 ↓
Graph detects this data dependency
 ↓
LLM generates B(result) as a parameter instead of reading shared state
```

The graph is what makes this data flow **visible** — without it, the LLM would miss implicit dependencies.

---

## Summary Flow
```
MAPA CSV → Neo4j Graph → subgraph query per paragraph
 │
 ┌───────────────┼───────────────┐
 │               │               │
 raw COBOL      data items      call deps
 └───────────────┼───────────────┘
 │
 LLM Prompt
 │
 Java Service Method
```
Neo4j becomes a **one-stop store** — both the structural graph AND the raw source for each paragraph. No separate file reads needed at migration time.

---

## Sample Business Logic Extraction

### Step 1 — MAPA CSV Gives You Line Ranges
```
program, paragraph, start_line, end_line
POLICY, CALC-PREMIUM, 245, 278
POLICY, APPLY-DISCOUNT, 279, 301
```

### Step 2 — Slice the Raw COBOL File
```python
def extract_paragraph(cobol_file_path, start_line, end_line):
    with open(cobol_file_path, 'r') as f:
        lines = f.readlines()
    # line numbers in CSV are 1-based
    return ''.join(lines[start_line - 1 : end_line])
```

### Step 3 — Store in Neo4j on the Paragraph Node
```cypher
MATCH (p:Paragraph {name: 'CALC-PREMIUM', program: 'POLICY'})
SET p.source_code = $raw_cobol_text
```

### Then at Migration Time
```python
# 1. Query graph for context
subgraph = neo4j.query(
    """
    MATCH (p:Paragraph {name: $name})
    OPTIONAL MATCH (p)-[:PERFORMS*]->(child:Paragraph)
    OPTIONAL MATCH (p)-[:READS|WRITES]->(d:DataItem)
    RETURN p.source_code, collect(child.source_code), collect(d)
    """,
    name="CALC-PREMIUM"
)

# 2. Build LLM prompt
prompt = f"""
Migrate this COBOL paragraph to a Java Spring Boot service method.
COBOL Source:
{subgraph['p.source_code']}
Dependencies it calls:
{subgraph['collect(child.source_code)']}
Data items used:
{subgraph['collect(d)']}
Generate Java method.
"""

# 3. Call LLM
java_code = llm.generate(prompt)
```

**Key Point**

```
MAPA CSV → line numbers → slice raw COBOL file → store in Neo4j
   │
   graph context ──────────────┤
   ▼
   LLM Prompt
```

---

## Agent Architecture

Yes, absolutely. An agentic approach is well-suited here because the migration pipeline has **distinct, separable concerns** that can run in parallel or hand off sequentially.

```
┌─────────────────────┐
│ Orchestrator Agent  │
│ (plans & delegates) │
└──────────┬──────────┘
   ┌──────────────┬────────┴───────┬──────────────┐
   ▼              ▼                ▼              ▼
┌─────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Ingestion   │ │ Analysis     │ │ Migration    │ │ Validation   │
│ Agent       │ │ Agent        │ │ Agent        │ │ Agent        │
└─────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

### The Agents

**1. Orchestrator Agent**  
**Role:** Breaks the program list into tasks, manages sequencing, retries failures.  
**Tools:** `task_queue`, `status_tracker`, `spawn_agent`  
**Input:** list of COBOL programs to migrate  
**Output:** final migration report

**2. Ingestion Agent**  
**Role:** Reads MAPA CSV + raw COBOL files, populates Neo4j.  
**Tools:** `read_csv`, `read_file(line_range)`, `neo4j_write`  
**Input:** MAPA `result.csv` + COBOL source directory  
**Output:** populated graph nodes (Program, Paragraph, DataItem, Copybook)

One instance per program, runs in parallel.

**3. Analysis Agent**  
**Role:** Traverses the graph, understands paragraph dependencies, classifies complexity.  
**Tools:** `neo4j_query`, `llm_call`  
**Input:** paragraph subgraph from Neo4j  
**Output:** complexity score, dependency tree, business intent summary  
Answers questions like:
- "Does this paragraph do I/O?"
- "How deep is the PERFORM chain?"
- "Is this paragraph shared across programs?"

**4. Migration Agent**  
**Role:** Converts a single paragraph (with context) to the target language.  
**Tools:** `neo4j_query`, `llm_call`, `code_writer`  
**Input:** paragraph source + dependency sources + data item types  
**Output:** Java/Python method + unit test skeleton  
Gets full context from graph before calling LLM — not just the paragraph in isolation.

**5. Validation Agent**  
**Role:** Checks generated code for correctness and completeness.  
**Tools:** `static_analyzer`, `test_runner`, `neo4j_query`  
**Input:** generated code + original COBOL source  
**Output:** pass/fail + issues list → triggers retry on Migration Agent if failed  
Checks:
- All PERFORM calls resolved
- All data items mapped
- Generated code compiles

### How They Interact
```
Orchestrator
 │
 ├─► Ingestion Agent (parallel, per program)
 │   └─► Neo4j populated
 │
 ├─► Analysis Agent (parallel, per paragraph)
 │   └─► complexity + intent stored on nodes
 │
 ├─► Migration Agent (parallel, per paragraph — ordered by dependency depth)
 │   └─► generated code stored on nodes
 │
 └─► Validation Agent (per paragraph)
     ├─► PASS → mark complete
     └─► FAIL → re-queue to Migration Agent with failure reason
```

### Why Agents Fit Here

| **Problem** | **Agent Solution** |
|---|---|
| 1000s of paragraphs | Parallel Migration Agents |
| Shared paragraphs across programs | Analysis Agent flags them, migrate once |
| Deep PERFORM chains | Analysis Agent orders work bottom-up |
| LLM hallucinations | Validation Agent catches + retries |
| Long-running pipeline | Orchestrator tracks state in Neo4j itself |

### Neo4j as Shared Memory
The graph acts as the **shared state** all agents read/write — paragraphs get a status property (pending → analysed → migrated → validated) that the Orchestrator uses to track progress without a separate database.

---

## IBM COBOL → Java Terminology Mapping

### Program Structure

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| **Program** | A single .cbl file | Class |
| **IDENTIFICATION DIVISION** | Program metadata (name, author, date) | Class declaration + Javadoc |
| **ENVIRONMENT DIVISION** | Machine/file configuration | `application.properties` / config |
| **DATA DIVISION** | All data declarations | Fields, instance variables |
| **PROCEDURE DIVISION** | All executable logic | Methods |
| **SECTION** | Grouping of paragraphs within a division | Inner class or logical block |
| **PARAGRAPH** | Named block of statements | Method |
| **SENTENCE** | One or more statements ending in `.` | Statement(s) in a method |

### Data Division Sections

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| **WORKING-STORAGE SECTION** | Program-level variables (persist for program lifetime) | Instance fields / class fields |
| **LOCAL-STORAGE SECTION** | Variables reset on each invocation | Local variables in a method |
| **LINKAGE SECTION** | Parameters passed from calling program | Method parameters |
| **FILE SECTION** | File record layouts | DTO / POJO mapped to file |
| **COMMUNICATION SECTION** | MQ / inter-program messaging data | Message payload class |

### Data Definition (PIC Clauses)

| **IBM COBOL Term** | **Example** | **Java Equivalent** |
|---|---|---|
| PIC 9(n) | Integer, n digits | `int` / `long` / `BigInteger` |
| PIC 9(n)V9(d) | Decimal with d decimal places | `BigDecimal` |
| PIC S9(n) | Signed integer | `int` / `long` |
| PIC X(n) | Alphanumeric string, n chars | `String` |
| PIC A(n) | Alphabetic only string | `String` |
| PIC 9(n) COMP | Binary integer (COMP/COMP-4) | `int` / `long` |
| PIC 9(n) COMP-3 | Packed decimal | `BigDecimal` |
| 88 level (condition name) | Named boolean value | `enum` or boolean constant |
| 01 level | Top-level record/group | Class / POJO |
| 05, 10... levels | Nested field within a group | Nested field or inner class |
| FILLER | Unnamed padding field | (ignored / padding bytes) |
| REDEFINES | Same memory, different layout | union-like — multiple views of a `byte[]` |
| OCCURS n TIMES | Fixed-length array | `T[]` array |
| OCCURS DEPENDING ON | Variable-length array | `List<T>` |
| VALUE clause | Initial value | Field initialiser (`= 0`, `= ""`) |

### Procedure Division — Control Flow

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| PERFORM | Call a paragraph once | Method call |
| PERFORM UNTIL | Loop until condition true | `while (!condition)` |
| PERFORM VARYING | Loop with counter | `for` loop |
| PERFORM TIMES | Repeat n times | `for (int i=0; i<n; i++)` |
| GO TO | Unconditional branch (avoid) | `return` / restructure |
| IF / ELSE / END-IF | Conditional | `if` / `else` |
| EVALUATE | Multi-way condition | `switch` / pattern matching |
| EVALUATE TRUE | Boolean multi-branch | `if` / `else if` chain |
| STOP RUN | Terminate program | `System.exit()` / return from main |
| EXIT PROGRAM | Return to caller | `return` |
| CONTINUE | No-op placeholder | `;` / empty block |
| NEXT SENTENCE | Skip to next sentence | `continue` (in loop context) |

### Data Operations

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| MOVE A TO B | Assign value | `b = a` |
| MOVE SPACES TO | Clear string | `b = ""` |
| MOVE ZEROS TO | Zero out numeric | `b = 0` |
| ADD A TO B | Addition | `b += a` |
| SUBTRACT A FROM B | Subtraction | `b -= a` |
| MULTIPLY A BY B | Multiplication | `b *= a` |
| DIVIDE A INTO B | Division | `b /= a` |
| COMPUTE | Arithmetic expression | `=` with expression |
| STRING ... INTO | Concatenate into variable | `String.join()` / `+` |
| UNSTRING ... INTO | Split string into variables | `String.split()` |
| INSPECT | Scan/replace characters | `String.replace()` / regex |
| INITIALIZE | Reset to default values | Constructor / `= 0` / `= ""` |

### File Handling

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| SELECT ... ASSIGN TO | Bind logical name to physical file | `new File(path)` |
| FD (File Description) | Record layout for a file | DTO / POJO |
| OPEN INPUT | Open file for reading | `BufferedReader` / `FileInputStream` |
| OPEN OUTPUT | Open file for writing | `BufferedWriter` / `FileOutputStream` |
| OPEN EXTEND | Append to file | `new FileWriter(f, true)` |
| READ ... INTO | Read one record | `reader.readLine()` |
| WRITE ... FROM | Write one record | `writer.write()` |
| CLOSE | Close file handle | `reader.close()` / try-with-resources |
| AT END | EOF condition | `readLine() == null` |
| VSAM KSDS | Keyed sequential dataset | Database table / `Map<K,V>` |
| VSAM ESDS | Entry-sequenced dataset | Sequential file / `List<Record>` |

### CICS (Online Transaction) Terms

| **IBM CICS Term** | **Description** | **Java / Spring Equivalent** |
|---|---|---|
| EXEC CICS RECEIVE MAP | Receive screen input | `@RequestBody` DTO |
| EXEC CICS SEND MAP | Send screen output | `@ResponseBody` / `ResponseEntity` |
| EXEC CICS READ | Read from VSAM/file | `repository.findById()` |
| EXEC CICS WRITE | Write to VSAM/file | `repository.save()` |
| EXEC CICS REWRITE | Update existing record | `repository.save()` (update) |
| EXEC CICS DELETE | Delete record | `repository.deleteById()` |
| EXEC CICS LINK | Call another CICS program (returns) | `@Service` method call |
| EXEC CICS XCTL | Transfer control (no return) | Redirect / forward |
| EXEC CICS RETURN | Return to caller / terminal | `return ResponseEntity` |
| EXEC CICS SYNCPOINT | Commit transaction | `@Transactional` commit point |
| EXEC CICS ABEND | Abnormal end | `throw new RuntimeException()` |
| EXEC CICS HANDLE CONDITION | Error handling | `try / catch` |
| COMMAREA | Data passed between CICS programs | Method parameter object / DTO |
| TWA (Task Work Area) | Task-scoped shared memory | Request-scoped `@Bean` |
| BMS Map | Screen layout definition | HTML form / REST DTO |
| Transaction ID | 4-char CICS transaction code | REST endpoint path / `@RequestMapping` |

### DB2 (Embedded SQL) Terms

| **IBM COBOL/DB2 Term** | **Description** | **Java / Spring Equivalent** |
|---|---|---|
| EXEC SQL ... END-EXEC | Embedded SQL block | `@Query` / JPQL / native SQL |
| SQLCA | SQL communication area (status) | `SQLException` / `@Transactional` |
| SQLCODE | SQL return code (0=OK, negative=error) | throws `SQLException` |
| CURSOR | Pointer for iterating result sets | `List<Entity>` / `Stream<Entity>` |
| OPEN CURSOR | Execute query | `repository.findAll()` |
| FETCH | Get next row | Iterator / for loop |
| CLOSE CURSOR | Release cursor | Auto-closed in Spring Data |
| HOST VARIABLE (:varname) | COBOL var used in SQL | JPQL parameter (`?1` / `:name`) |
| DCLGEN | Auto-generated copybook for table | JPA `@Entity` class |

### JCL (Batch Job) Terms

| **IBM JCL Term** | **Description** | **Java / Spring Batch Equivalent** |
|---|---|---|
| JOB card | Job definition and metadata | `@SpringBootApplication` / Job bean |
| EXEC PGM= | Execute a program | Tasklet / Step |
| EXEC PROC= | Execute a catalogued procedure | Reusable Step / Flow |
| DD statement | Data definition (file assignment) | `FlatFileItemReader` / `ItemWriter` |
| SYSOUT | Print/log output | Slf4j logger / stdout |
| DISP=SHR | Shared read access to dataset | Read-only file open |
| DISP=(NEW,CATLG) | Create and catalogue new dataset | Create new output file |
| STEPLIB / JOBLIB | Program library | Classpath / Maven dependency |
| IF STEP.RC = 0 | Step return code check | `JobExecutionDecider` |
| RESTART | Resume from checkpoint | Spring Batch restart from last checkpoint |
| SORT FIELDS= | Sort utility step | Java `Comparator` / `sorted()` |

### Compile & Runtime Terms

| **IBM COBOL Term** | **Description** | **Java Equivalent** |
|---|---|---|
| **Copybook** | Shared data layout (.cpy) | Interface / shared DTO / library class |
| **Load module** | Compiled executable | `.jar` / `.class` |
| **PDS** (Partitioned Dataset) | Library of members | Package / directory |
| **CICS Region** | Runtime server for transactions | Spring Boot application server (Tomcat) |
| **Abend** | Abnormal end / crash | `RuntimeException` / JVM crash |
| **Return code** | Exit status (0=OK, non-zero=error) | Exception / HTTP status code |
| **SYSOUT** | Console / print output | `System.out` / logger |
| **Linkage Editor** | Links compiled modules | Maven/Gradle dependency resolution |
