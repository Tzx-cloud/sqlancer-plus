import os
import argparse
import random
import yaml
import time
from collections import defaultdict
from langchain_community.document_loaders import WebBaseLoader
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.tools import Tool
from langchain_google_community import GoogleSearchAPIWrapper
from langchain.chains.combine_documents import create_stuff_documents_chain

DBMS_MAPPING = defaultdict(lambda: "Unknown", {
    "duckdb" : "DuckDB",
    "postgresql" : "PostgreSQL",
    "postgres" : "PostgreSQL",
    "cedardb" : "CedarDB",
    "cratedb" : "CrateDB",
    "cockroachdb" : "CockroachDB",
    "sqlite" : "SQLite",
})

OVERWRITE = True
DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_DIR = os.path.join(DIR, "..", "dbconfigs")
YAML_DIR = CONFIG_DIR + "/url.yml"
os.environ["OPENAI_API_KEY"] = os.getenv("OPENAI_API_KEY")

with open(CONFIG_DIR + "/GOOGLE_API.txt") as f:
    os.environ["GOOGLE_API_KEY"] = f.read().strip()

with open(CONFIG_DIR + "/GOOGLE_CSE.txt") as f:
    os.environ["GOOGLE_CSE_ID"] = f.read().strip()

PROMPTS = {
    "datatype_general" : [("system", "What are the data types in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me example values and definitions in CREATE TABLE statements")],
    "datatype_specific" : [("system", "What are {topic} for {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me only names and examples. Please list all the possible values.")],
    "function_general" : [("system", "What are the functions in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me example usage and syntax.")],
    "function_specific" : [("system", "What are {topic} in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me as many example usage and syntax as possible.")],
    "all_specific" : [("system", "What are {topic} in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me example usage and syntax.")],
    "clause_specific" : [("system", "What are {topic} in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me examples usage, syntax and detailed keywords.")],
    "command_specific" : [("system", "What are {topic} in {DBMS} based on the documentation: \\n\\n {context}.\\n\\n Give me exampes.")],
}

def get_docs_by_URL(url: str):
    loader = WebBaseLoader(url)
    return loader.load()

def search_google_get_first_link(query: str):
    def top5_results(query: str):
        return search.results(query, 5)
    search = GoogleSearchAPIWrapper()
    tool = Tool(
        name="google_search",
        description="Search Google for recent results.",
        func=top5_results,
    )
    print(f"Search result for {query}:")
    result = tool.run(query)
    return result[0]["link"]

def get_urls_from_yaml(dbms:str, feature: str, dir: str, topic: str = "") -> list:
    with open(dir) as f:
        url_yaml = yaml.safe_load(f)
    
    # get the dict to the level of feature
    try:
        feature_dict = url_yaml[dbms][feature]
    except (KeyError, TypeError):
        # TODO: automatically learn the feature and update the yaml file
        url = search_google_get_first_link(f"{dbms} {feature} documentation")
        print(f"Automatically found the URL for {dbms} {feature} documentation: {url}")
        url_yaml[dbms][feature] = {'overview' : [url]}
        feature_dict = url_yaml[dbms][feature]
        # raise ValueError(f"Please provide a valid feature. Available options are: {list(url_yaml[dbms].keys())}")
    
    # get the fine-grained urls
    urls = []
    if topic == "" or topic == "overview":
        try:
            urls = feature_dict['overview']
        except KeyError:
            raise ValueError(f"Please provide a valid feature. Available options are: {list(feature_dict.keys())}")
    else:
        try:
            urls = feature_dict[topic]
        except KeyError:
            url = search_google_get_first_link(f"{dbms} documentation for {topic}")
            print(f"Automatically found the URL for {dbms} documentation for {feature} {topic}: {url}")
            url_yaml[dbms][feature][topic] = [url]
            urls = [url]
            # raise ValueError(f"Please provide a valid topic. Available options are: {list(feature.keys())}")
        
    # update the yaml file
    if OVERWRITE:
        with open(dir, "w") as f:
            yaml.dump(url_yaml, f)
    else:
        backup_dir = CONFIG_DIR + "/urls"
        os.makedirs(backup_dir, exist_ok=True)
        timestamp = time.strftime("%Y%m%d-%H%M")
        with open(f"{backup_dir}/url_{timestamp}.yml", "w") as f:
            yaml.dump(url_yaml, f)
    return urls


def learn_reference(docs, dbms: str, chain):
    result = chain.invoke({"context": docs, "DBMS": dbms})
    print(result)
    
def learn_reference_with_topic(docs, dbms: str, chain, topic: str):
    result = chain.invoke({"context": docs, "DBMS": dbms, "topic": topic})
    print(result)

if __name__ == "__main__":
    argparser = argparse.ArgumentParser()
    argparser.add_argument("--model", type=str, default="gpt-4o-mini")
    argparser.add_argument("--dbms", type=str, default="")
    argparser.add_argument("--feature", type=str, default="", choices=["datatype", "function", "command", "clause"])
    argparser.add_argument("--topic", type=str, default="")
    argparser.add_argument("--learn", action="store_true")
    argparser.add_argument("--debug", action="store_true")
    argparser.add_argument("--yaml", type=str, default="")
    args = argparser.parse_args()

    avail_dbms = list(DBMS_MAPPING.keys())
    dbms = DBMS_MAPPING[args.dbms.lower()]
    if dbms == "Unknown":
        raise ValueError(f"Please provide an existing DBMS name. Available options are: {avail_dbms}")

    if args.topic == "overview":
        prompt_tag = f"{args.feature}_general"
    else:
        prompt_tag = f"{args.feature}_specific"
    
    
    if args.yaml != "":
        yaml_dir = CONFIG_DIR + "/" + args.yaml
    else:
        yaml_dir = YAML_DIR
    
    # Get the URL and the docs
    urls = get_urls_from_yaml(dbms, args.feature, yaml_dir, args.topic)
    url = random.choice(urls)
    docs = get_docs_by_URL(url)
    if args.debug:
        print(docs[0].page_content)

    if not args.learn:
        print("Skip to learn the reference.")
        exit()

    # Creating the chain
    llm = ChatOpenAI(model=args.model)
    prompt = ChatPromptTemplate.from_messages(PROMPTS[prompt_tag])
    chain = create_stuff_documents_chain(llm, prompt)

    # Learning the reference
    if prompt_tag.endswith("general"):
        learn_reference(docs, dbms, chain)
    elif prompt_tag.endswith("specific"):
        learn_reference_with_topic(docs, dbms, chain, args.topic)
    
    