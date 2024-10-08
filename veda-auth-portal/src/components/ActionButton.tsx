import { Icon, Button } from "@chakra-ui/react";
interface ActionButtonProps {
  onClick: () => void;
  children: React.ReactNode;
  icon?: any;
  size?: string;
}

export const ActionButton = ({ onClick, icon, children, size }: ActionButtonProps) => {
  return (
    <Button
      bg='black'
      color='white'
      _hover={{
        bg: 'gray.600'
      }}
      onClick={onClick}
      px={2}
      py={1}
      fontSize='sm'
      size={size}
    >
      {icon && <Icon as={icon} mr={1} fontSize='lg' />}
      {children}
    </Button>
  )
}
