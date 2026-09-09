package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author mthgp26
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        // [Failure Case] 用户没有输入任何参数
        if (args.length < 1) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }

        String firstArg = args[0];
        switch(firstArg) {
            case "init": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                if (args.length != 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository.init();
                break;
            }
            case "add": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                String filename = args[1];
                repo.add(filename);
                repo.save();
                break;
            }
            case "commit": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                // [Failure Case] log message为空，与上面的Failure Case合并检查
                if (args.length < 2 || args[1].trim().isEmpty()) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                String message = args[1];
                repo.commit(message);
                repo.save();
                break;
            }
            case "rm": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                String filename = args[1];
                repo.rm(filename);
                repo.save();
                break;
            }
            case "log": {
                // [Failure Case]  用户输入的命令操作数数量或格式错误
                if (args.length != 1) {
                    System.out.println("Incorrect operands");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                repo.log();
                repo.save(); // 其实也可以不调用save，因为log()不会改变存储的信息
                break;
            }
            case "global-log": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                if (args.length != 1) {
                    System.out.println("Incorrect operands");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                repo.globalLog();
                repo.save();
                break;
            }
            case "find": {
                // [Failure Case] 用户输入的命令操作数数量或格式错误
                if (args.length != 2) {
                    System.out.println("Incorrect operands");
                    System.exit(0);
                }

                String message = args[1];
                Repository repo = Repository.load();
                repo.find(message);
                repo.save();
                break;
            }
            case "status": {
                if (args.length != 1) {
                    System.out.println("Incorrect operands");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                repo.status();
                repo.save();
                break;
            }
            case "checkout": {
                Repository repo = Repository.load();

                // check args
                if (args.length == 2) { // java gitlet.Main checkout [分支名]
                    String branchName = args[1];
                    repo.checkout(2, branchName, null, null);
                } else if (args.length == 3) { // java gitlet.Main checkout -- [文件名]
                    if (!args[1].equals("--")) {
                        System.out.println("Incorrect operands.");
                        System.exit(0);
                    }
                    String filename = args[2];
                    repo.checkout(0, null, null, filename);
                } else if (args.length == 4) {
                    if (!args[2].equals("--")) {
                        System.out.println("Incorrect operands.");
                        System.exit(0);
                    }
                    String commitID = args[1];
                    String filename = args[3];
                    repo.checkout(1, null, commitID, filename);
                } else {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                repo.save();
                break;
            }
            case "branch": {
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                String branchName = args[1];
                Repository repo = Repository.load();

                repo.branch(branchName);
                repo.save();
                break;
            }
            case "rm-branch": {
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                String branchName = args[1];
                Repository repo = Repository.load();
                repo.rmBranch(branchName);
                repo.save();
                break;
            }
            case "reset": {
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                String commitID = args[1];
                Repository repo = Repository.load();
                repo.reset(commitID);
                repo.save();
                break;
            }
            case "merge": {
                if (args.length != 2) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }

                Repository repo = Repository.load();
                String branchName = args[1];
                repo.merge(branchName);
                repo.save();
                break;
            }
            // [Failure Case] 用户输入了一个不存在的命令
            default:
                System.out.println("No command with that name exists.");
                System.exit(0);
        }
    }

    private static Repository getRepo(Repository repo) {
        return repo;
    }
}
